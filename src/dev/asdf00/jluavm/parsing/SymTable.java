package dev.asdf00.jluavm.parsing;

import dev.asdf00.jluavm.exceptions.loading.LuaSemanticException;
import dev.asdf00.jluavm.parsing.container.*;
import dev.asdf00.jluavm.parsing.ir.controlflow.BreakNode;
import dev.asdf00.jluavm.parsing.ir.controlflow.GotoNode;
import dev.asdf00.jluavm.parsing.ir.controlflow.LabelNode;
import dev.asdf00.jluavm.utils.Triple;
import dev.asdf00.jluavm.utils.Tuple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Stack;

public class SymTable {
    private int idSupplier = 0;
    private VarScope rootScope = null;
    private VarScope curScope = null;
    private final HashMap<VarScope, Stack<BreakNode>> breaksToPatch = new HashMap<>();

    public void enterScope(boolean isFunctionBorder, boolean isLoop) {
        var prev = curScope;
        curScope = new VarScope(curScope, idSupplier++, isFunctionBorder, isLoop);
        if (rootScope == null) {
            rootScope = curScope;
        }
        if (prev != null) {
            prev.children.add(curScope);
        }
        yetToFixGotos.push(new HashMap<>());
        dangerZone.push(new ArrayList<>());
    }

    public VarScope exitScope() {
        var rVal = curScope;
        curScope = curScope.exitScope();

        // make inner yet to fix gotos visible to later label definitions do final checks if we exit a function scope
        if (curScope.isFunctionBorder) {
            // function exit
            if (!yetToFixGotos.peek().isEmpty()) {
                throw new LuaSemanticException(yetToFixGotos.peek().values().stream().findAny().get().stream().findAny().get().z(), "no goto target found");
            }
            // remove inner map
            yetToFixGotos.pop();
        } else {
            // exit inner scope
            var inner = yetToFixGotos.pop();
            yetToFixGotos.peek().putAll(inner);
        }
        dangerZone.pop();

        // fixup all inner breaks
        if (rVal.isLoop) {
            var patch = breaksToPatch.get(rVal);
            if (patch != null) {
                int curClosables = getCurFuncClosableCnt();
                for (var b : patch) {
                    // the closable count of this break is initialized with the total amount of closables at that time,
                    // but we only want to close values that are not live anymore after the loop scope
                    b.closeCnt -= curClosables;
                }
                breaksToPatch.remove(rVal);
            }
        }
        return rVal;
    }

    public int getCurFuncClosableCnt() {
        VarScope c = curScope;
        int cnt = 0;
        while (!c.isFunctionBorder) {
            cnt += c.getClosableCount();
            c = c.parent;
        }
        cnt += c.getClosableCount();
        return cnt;
    }

    public VarScope getNextLoop() {
        return curScope.getNextLoopScope();
    }

    public LabelNode addLabel(Token label, LinkedHashMap<String, ArrayList<GotoNode>> fixupList) {
        return curScope.addLabel(label, fixupList);
    }

    private LabelInfo getLabel(String ident) {
        return curScope.getLabel(ident);
    }

    public VarInfo[][] getDefinedVars() {
        return curScope.getDefinedVars();
    }

    public SpecificVarInfo add(Token ident, boolean isConst, boolean isClosable) {
        return curScope.add(ident, isConst, isClosable);
    }

    public SpecificVarInfo get(String ident) {
        return curScope.get(ident, false);
    }

    public BreakNode generateBreakNode() {
        var cs = curScope;
        int escapeCnt = 0;
        while (!cs.isFunctionBorder && cs.isLoop) {
            escapeCnt++;
        }
        if (cs.isFunctionBorder) {
            return null;
        }
        assert cs.isLoop;
        escapeCnt++;
        var b = new BreakNode(escapeCnt, getCurFuncClosableCnt());
        breaksToPatch.computeIfAbsent(cs, s -> new Stack<>()).push(b);
        return b;
    }

    public final Stack<HashMap<String, ArrayList<Triple<GotoNode, int[], Position>>>> yetToFixGotos = new Stack<>();

    public int[] getCurFuncClsCntPerScope() {
        var closeDefList = new ArrayList<Integer>();
        var cs = curScope;
        while (!cs.isFunctionBorder) {
            closeDefList.add(cs.getClosableCount());
            cs = cs.parent;
        }
        closeDefList.add(cs.getClosableCount());
        return closeDefList.stream().mapToInt(i -> i).toArray();
    }

    public GotoNode generateGoto(Token gttk) {
        var lbl = getLabel(gttk.stVal());
        if (lbl != null) {
            // back-jump
            int[] perScopeCloseCnt = getCurFuncClsCntPerScope();
            int toClose = 0;
            int lcpsl = lbl.closablesPerScope.length;
            for (int i = perScopeCloseCnt.length - 1; i >= lcpsl; i--) {
                toClose += perScopeCloseCnt[i];
            }
            toClose += perScopeCloseCnt[lcpsl - 1] - lbl.closablesPerScope[lcpsl - 1];
            return new GotoNode("###labelpatch_" + gttk.stVal() + "###", perScopeCloseCnt.length - lcpsl, toClose, 0);
        } else {
            // forward jump
            /*-
             * How to patch this goto node:
             *  - subtract label scope depth from goto cDefs.length -> scope exits
             *  - sum all cDefs the goto has seen in scopes lower than the label -> closableCnt
             *  - subtract cloables defined in label scope at goto from defined at label -> closePatchCnt
             */
            var cDefs = getCurFuncClsCntPerScope();
            var g = new GotoNode("###labelpatch_" + gttk.stVal() + "###", 0, 0, 0);
            yetToFixGotos.peek().computeIfAbsent(gttk.stVal(), k -> new ArrayList<>()).add(new Triple<>(g, cDefs, gttk.pos()));
            return g;
        }
    }

    public LabelNode generateLabel(Token t) {
        // TODO: register label in sym tab and throw if already defined
        int[] ccps = getCurFuncClsCntPerScope();
        var info = new LabelInfo(t.stVal(), ccps);
        var node = new LabelNode(info);
        // check for fixup
        var gotos = yetToFixGotos.peek().get(t.stVal());
        if (gotos == null) {
            // no gotos to fix, we are done here
            return node;
        }
        // fix gotos
        for (Triple<GotoNode, int[], Position> gtt : gotos) {
            int scopeExits = gtt.y().length - ccps.length;
            int toClose = 0;
            for (int i = gtt.y().length - 1; i >= ccps.length; i--) {
                toClose -= gtt.y()[i];
            }
            toClose += gtt.y()[ccps.length - 1];
            int toPatch = ccps[ccps.length - 1] - gtt.y()[ccps.length - 1];
            if (toPatch > 0) {
                // we jump into scopes of local closable variables which is only allowed if the label is the last statement in a block
                dangerZone.peek().add(new Tuple<>(gtt.x(), gtt.z()));
            }
            gtt.x().scopeExits = scopeExits;
            gtt.x().closableCnt = toClose;
            gtt.x().closePatchCnt = toPatch;
        }
        yetToFixGotos.peek().remove(t.stVal());
        return node;
    }

    private final Stack<ArrayList<Tuple<GotoNode, Position>>> dangerZone = new Stack<>();

    public void labelNotLast() {
        if (!dangerZone.peek().isEmpty()) {
            // jump into scope of local closable variable
            // TODO throw error
        }
    }
}
