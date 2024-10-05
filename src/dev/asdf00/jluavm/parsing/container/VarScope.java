package dev.asdf00.jluavm.parsing.container;

import dev.asdf00.jluavm.parsing.exceptions.LuaSemanticException;
import dev.asdf00.jluavm.parsing.ir.controlflow.GotoNode;
import dev.asdf00.jluavm.parsing.ir.controlflow.LabelNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

public class VarScope {
    private final VarScope parent;
    public final ArrayList<VarScope> children = new ArrayList<>();
    public final int id;
    public final boolean isFunctionBorder;
    public final boolean isLoop;
    public boolean containsClosable = false;
    private final LinkedHashMap<String, VarInfo> names = new LinkedHashMap<>();
    private final LinkedHashMap<String, LabelInfo> labels = new LinkedHashMap<>();

    public VarScope(VarScope parent, int id, boolean isFunctionBorder, boolean isLoop) {
        this.parent = parent;
        this.id = id;
        this.isFunctionBorder = isFunctionBorder;
        this.isLoop = isLoop;
    }

    public VarScope exitScope() {
        return parent;
    }

    public boolean add(Token ident, boolean isConst, boolean isClosable) {
        if (names.containsKey(ident)) {
            return false;
        }
        containsClosable |= isClosable;
        names.put(ident.stVal(), new VarInfo(ident.pos().sourcePt(), "_" + id + '$' + ident, isConst, isClosable));
        return true;
    }

    public VarInfo get(String ident, boolean crossedFunctionBorder) {
        var rVal = names.get(ident);
        if (rVal == null) {
            return parent == null ? null : parent.get(ident, crossedFunctionBorder || isFunctionBorder);
        } else {
            if (crossedFunctionBorder) {
                rVal.setInClosure();
            }
            return rVal;
        }
    }

    private ArrayList<VarInfo[]> getDefVarsRecursive(ArrayList<VarInfo[]> list) {
        if (!isFunctionBorder && parent != null) {
            parent.getDefVarsRecursive(list);
        }
        final var myArray = new VarInfo[names.size()];
        final int[] idx = new int[]{0};
        names.forEach((k, v) -> myArray[idx[0]++] = v);
        list.add(myArray);
        return list;
    }

    public VarInfo[][] getDefinedVars() {
        return getDefVarsRecursive(new ArrayList<>()).toArray(VarInfo[][]::new);
    }

    public LabelNode addLabel(Token ident, LinkedHashMap<String, ArrayList<GotoNode>> fixupList) {
        if (labels.containsKey(ident.stVal())) {
            var l = labels.get(ident.stVal());
            throw new LuaSemanticException(ident.pos(), "Label '%s' defined twice at %s and %s"
                    .formatted(ident.stVal(), l.pos.sourcePos(), ident.pos().sourcePos()));
        }
        VarInfo[][] defVars = getDefinedVars();
        var info = new LabelInfo(ident.pos(), defVars, id, labels.size() + 1, isLoop);
        var lNode = new LabelNode(info);
        var toFix = fixupList.get(ident.stVal());
        if (toFix != null) {
            // fix up forward jumps to this label
            info.isUsed = true;
            int curDIdx = defVars.length - 1;
            VarInfo[] curScopeVars = defVars[curDIdx];
            for (GotoNode gt : toFix) {
                VarInfo[] gtVarsInCurScope = gt.definedVars[curDIdx];
                if (curScopeVars.length > gtVarsInCurScope.length) {
                    // the only way this forward jump is valid is, if this label is at the end of the block,
                    // but we don't know that yet, so we mark this goto as potentially problematic
                    lNode.setMaybeInvalid(gt, curScopeVars[gtVarsInCurScope.length]);
                    // this goto needs to close all open variables from inner scopes AND THE CURRENT SCOPE
                    gt.toClose = getClosablesForFixup(gt, curDIdx, true);
                } else {
                    // this jump may need to close variables from inner scopes, BUT NOT THE CURRENT SCOPE
                    gt.toClose = getClosablesForFixup(gt, curDIdx, false);
                }
                gt.label = lNode;
            }
            fixupList.remove(ident.stVal());
        }
        return lNode;
    }

    public LabelInfo getLabel(String ident) {
        // upwards search until function border
        var rVal = labels.get(ident);
        if (rVal == null && !isFunctionBorder && parent != null) {
            rVal = parent.getLabel(ident);
        }
        rVal.isUsed = true;
        return rVal;
    }

    public String toFullString() {
        return "VarScope {parent=%s, id=%s, funcBorder=%s, closable=%s, names=%s, children=%s}".formatted(
                parent == null ? -1 : parent.id, id, isFunctionBorder, containsClosable, names.toString(),
                "[" + children.stream().map(VarScope::toFullString).collect(Collectors.joining(",\n")) + "]");
    }

    private static VarInfo[] getClosablesForFixup(GotoNode gt, int curDIdx, boolean includeCurrent) {
        var closableList = new ArrayList<VarInfo>();
        for (int scp = gt.definedVars.length - 1; includeCurrent ? scp >= curDIdx : scp > curDIdx; scp--) {
            for (int vIdx = gt.definedVars[scp].length - 1; vIdx >= 0; vIdx--) {
                var cVar = gt.definedVars[scp][vIdx];
                if (cVar.isClosable()) {
                    closableList.add(cVar);
                }
            }
        }
        return closableList.toArray(VarInfo[]::new);
    }
}
