package dev.asdf00.jluavm.parsing;

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
import java.util.function.Supplier;

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
    }

    public VarScope exitScope() {
        var rVal = curScope;
        curScope = curScope.exitScope();
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

    public Tuple<LabelInfo, Integer> getLabel(String ident) {
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

    public final HashMap<String, ArrayList<Triple<GotoNode, int[], Position>>> yetToFixGotos = new HashMap<>();

    public GotoNode generateGoto(String target, Position pos) {
        var lbl = getLabel(target);
        if (lbl != null) {
            // back-jump
            int toClose = 0;
            var cs = curScope;
            for (int togo = lbl.y(); togo > 0; togo--) {
                toClose += cs.getClosableCount();
                cs = cs.parent;
            }
            toClose += cs.getClosableCount() - lbl.x().definedClosablesInScope;
            return new GotoNode("###labelpatch_" + target + "###", lbl.y(), toClose, 0);
        } else {
            // forward jump
            var cs = curScope;
            var closeDefList = new ArrayList<Integer>();
            while (!cs.isFunctionBorder) {
                closeDefList.add(cs.getClosableCount());
                cs = cs.parent;
            }
            closeDefList.add(cs.getClosableCount());
            var cDefs = closeDefList.stream().mapToInt(i -> i).toArray();
            /*-
             * How to patch this goto node:
             *  - subtract label scope depth from goto cDefs.length -> scope exits
             *  - sum all cDefs the goto has seen in scopes lower than the label -> closableCnt
             *  - subtract cloables defined in label scope at goto from defined at label -> closePatchCnt
             */
            var g = new GotoNode("###labelpatch_" + target + "###", 0, 0, 0);
            yetToFixGotos.computeIfAbsent(target, k -> new ArrayList<>()).add(new Triple<>(g, cDefs, pos));
            return g;
        }
    }

    public void labelNotLast() {

    }
}
