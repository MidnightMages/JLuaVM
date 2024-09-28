package dev.asdf00.jluavm.parsing;

import dev.asdf00.jluavm.parsing.container.VarInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

public class SymTable {
    private int idSupplier = 0;
    private VarScope curScope;
    private VarScope prevScope;

    public void enterScope(ArrayList<String> params) {
        prevScope = curScope;
        curScope = new VarScope(curScope, idSupplier++, params != null);
        if (prevScope != null) {
            prevScope.children.add(curScope);
        }
        if (params != null) {
            for (var name : params) {
                curScope.add(name, false, false);
            }
        }
    }

    public void exitScope() {
        prevScope = curScope;
        curScope = curScope.exitScope();
    }

    public boolean add(String ident, boolean isConst, boolean isClosable) {
        return curScope.add(ident, isConst, isClosable);
    }

    public VarInfo get(String ident) {
        return curScope.get(ident, false);
    }

    public VarInfo getFromPrevScope(String ident) {
        return prevScope.get(ident, false);
    }

    private static class VarScope {
        private final VarScope parent;
        public final ArrayList<VarScope> children = new ArrayList<>();
        private final int id;
        private final boolean isFunctionBorder;
        private boolean containsClosable = false;
        private final HashMap<String, VarInfo> names = new HashMap<>();

        public VarScope(VarScope parent, int id, boolean isFunctionBorder) {
            this.parent = parent;
            this.id = id;
            this.isFunctionBorder = isFunctionBorder;
        }

        public VarScope exitScope() {
            return parent;
        }

        public boolean add(String ident, boolean isConst, boolean isClosable) {
            if (names.containsKey(ident)) {
                return false;
            }
            containsClosable |= isClosable;
            names.put(ident, new VarInfo("_" + id + '$' + ident, isConst, isClosable));
            return true;
        }

        public VarInfo get(String ident, boolean crossedFunctionBorder) {
            var rval = names.get(ident);
            if (rval == null) {
                return parent == null ? null : parent.get(ident, crossedFunctionBorder || isFunctionBorder);
            } else {
                if (crossedFunctionBorder) {
                    rval.setInClosure();
                }
                return rval;
            }
        }

        public String toFullString() {
            return "VarScope {parent=%s, id=%s, funcBorder=%s, closable=%s, names=%s, children=%s}".formatted(
                    parent == null ? -1 : parent.id, id, isFunctionBorder, containsClosable, names.toString(),
                    "[" + children.stream().map(VarScope::toFullString).collect(Collectors.joining(",\n")) + "]");
        }
    }
}
