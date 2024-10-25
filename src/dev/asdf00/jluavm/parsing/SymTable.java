package dev.asdf00.jluavm.parsing;

import dev.asdf00.jluavm.parsing.container.*;
import dev.asdf00.jluavm.parsing.ir.controlflow.GotoNode;
import dev.asdf00.jluavm.parsing.ir.controlflow.LabelNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class SymTable {
    private int idSupplier = 0;
    private VarScope rootScope = null;
    private VarScope curScope = null;

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
        return rVal;
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

    public boolean add(Token ident, boolean isConst, boolean isClosable) {
        return curScope.add(ident, isConst, isClosable);
    }

    public SpecificVarInfo get(String ident) {
        return curScope.get(ident, false);
    }


}
