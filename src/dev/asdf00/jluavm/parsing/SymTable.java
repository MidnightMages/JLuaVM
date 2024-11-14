package dev.asdf00.jluavm.parsing;

import dev.asdf00.jluavm.exceptions.loading.LuaSemanticException;
import dev.asdf00.jluavm.parsing.container.*;
import dev.asdf00.jluavm.parsing.ir.controlflow.BreakNode;
import dev.asdf00.jluavm.parsing.ir.controlflow.GotoNode;
import dev.asdf00.jluavm.parsing.ir.controlflow.LabelNode;
import dev.asdf00.jluavm.utils.Quadruple;
import dev.asdf00.jluavm.utils.Tuple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

public class SymTable {
    private int idSupplier = 0;
    private VarScope rootScope = null;
    private VarScope curScope = null;
    private final HashMap<VarScope, Stack<BreakNode>> breaksToPatch = new HashMap<>();
    private final Stack<HashMap<String, ArrayList<Quadruple<GotoNode, int[], int[], Position>>>> yetToFixGotos = new Stack<>();
    private final Stack<ArrayList<Tuple<GotoNode, Position>>> dangerZone = new Stack<>();

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
        curScope = curScope.parent;

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

    public int[] getCurFuncLocalsCntPerScope() {
        var closeDefList = new ArrayList<Integer>();
        var cs = curScope;
        while (!cs.isFunctionBorder) {
            closeDefList.add(cs.getLocalsCount());
            cs = cs.parent;
        }
        closeDefList.add(cs.getLocalsCount());
        return closeDefList.stream().mapToInt(i -> i).toArray();
    }

    public SpecificVarInfo add(Token ident, boolean isConst, boolean isClosable) {
        return curScope.add(ident, isConst, isClosable);
    }

    public SpecificVarInfo get(String ident) {
        return curScope.get(ident, false);
    }

    private LabelInfo addLabel(Token label) {
        return curScope.addLabel(label, getCurFuncLocalsCntPerScope(), getCurFuncClsCntPerScope());
    }

    private LabelInfo getLabel(String ident) {
        return curScope.getLabel(ident);
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

    public GotoNode generateGoto(Token gttk) {
        var lbl = getLabel(gttk.stVal());
        if (lbl != null) {
            // back-jump
            int[] perScopeCloseCnt = getCurFuncClsCntPerScope();
            int[] perScopeLocalCnt = getCurFuncLocalsCntPerScope();
            int toClose = 0;
            int localDiff = 0;
            int lcpsl = lbl.closablesPerScope.length;
            for (int i = perScopeCloseCnt.length - 1; i >= lcpsl; i--) {
                toClose += perScopeCloseCnt[i];
                localDiff += perScopeLocalCnt[i];
            }
            toClose += perScopeCloseCnt[lcpsl - 1] - lbl.closablesPerScope[lcpsl - 1];
            localDiff += perScopeLocalCnt[lcpsl - 1] - lbl.localsPerScope[lcpsl - 1];
            int localsForLabel = 0;
            for (int lc : lbl.localsPerScope) {
                localsForLabel += lc;
            }
            return new GotoNode(gotoPatchFor(gttk.stVal()), perScopeCloseCnt.length - lcpsl, toClose, 0, localsForLabel, localDiff);
        } else {
            // forward jump
            /*-
             * How to patch this goto node:
             *  - subtract label scope depth from goto cDefs.length -> scope exits
             *  - sum all cDefs the goto has seen in scopes lower than the label -> closableCnt
             *  - subtract cloables defined in label scope at goto from defined at label -> closePatchCnt
             */
            var cDefs = getCurFuncClsCntPerScope();
            var lDefs = getCurFuncLocalsCntPerScope();
            var g = new GotoNode(gotoPatchFor(gttk.stVal()));
            yetToFixGotos.peek().computeIfAbsent(gttk.stVal(), k -> new ArrayList<>()).add(new Quadruple<>(g, lDefs, cDefs, gttk.pos()));
            return g;
        }
    }

    public LabelNode generateLabel(Token t) {
        var info = addLabel(t);
        var node = new LabelNode(info);

        // check for fixup
        var gotos = yetToFixGotos.peek().get(t.stVal());
        if (gotos == null) {
            // no gotos to fix, we are done here
            return node;
        }

        // fix gotos
        int[] ccps = getCurFuncClsCntPerScope();
        int[] clps = getCurFuncLocalsCntPerScope();
        for (Quadruple<GotoNode, int[], int[], Position> gtt : gotos) {
            int scopeExits = gtt.y().length - ccps.length;
            int toClose = 0;
            for (int i = gtt.y().length - 1; i >= ccps.length; i--) {
                toClose -= gtt.y()[i];
            }
            toClose += gtt.y()[ccps.length - 1];
            int toPatch = ccps[ccps.length - 1] - gtt.y()[ccps.length - 1];
            if (gtt.x()[clps.length - 1] < clps[clps.length - 1]) {
                // we jump into scopes of local variables which is only allowed if the label is the last statement in a block
                dangerZone.peek().add(new Tuple<>(gtt.w(), gtt.z()));
            }
            gtt.w().scopeExits = scopeExits;
            gtt.w().closableCnt = toClose;
            gtt.w().closePatchCnt = toPatch;
        }
        yetToFixGotos.peek().remove(t.stVal());
        return node;
    }

    public void labelNotLast() {
        if (!dangerZone.peek().isEmpty()) {
            // jump into scope of local variable
            var elem = dangerZone.peek().get(0);
            throw new LuaSemanticException(elem.y(), "jump into scope of local variable");
        }
    }

    public static String gotoPatchFor(String ident) {
        // TODO: generate unique patch for each and every label info even if ident is the same
        return "###labelpatch_" + ident + "###";
    }
}
