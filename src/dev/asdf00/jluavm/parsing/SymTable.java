package dev.asdf00.jluavm.parsing;

import dev.asdf00.jluavm.parsing.container.*;
import dev.asdf00.jluavm.parsing.ir.controlflow.BreakNode;
import dev.asdf00.jluavm.parsing.ir.controlflow.GotoNode;
import dev.asdf00.jluavm.parsing.ir.controlflow.LabelNode;
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

    public LabelInfo getLabel(String ident) {
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
}
