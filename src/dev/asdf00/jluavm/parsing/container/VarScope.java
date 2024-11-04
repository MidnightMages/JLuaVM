package dev.asdf00.jluavm.parsing.container;

import dev.asdf00.jluavm.exceptions.loading.LuaSemanticException;
import dev.asdf00.jluavm.parsing.ir.controlflow.GotoNode;
import dev.asdf00.jluavm.parsing.ir.controlflow.LabelNode;

import java.util.*;
import java.util.stream.Collectors;

public class VarScope {
    private final VarScope parent;
    private final int baseIdx;
    public final ArrayList<VarScope> children = new ArrayList<>();
    public final int id;

    private final LinkedHashMap<String, VarInfo> names = new LinkedHashMap<>();
    private final LinkedHashMap<String, LabelInfo> labels = new LinkedHashMap<>();

    public final boolean isFunctionBorder;
    public final boolean isLoop;
    public boolean containsClosable = false;

    private final LinkedHashMap<VarInfo, Integer> captured;

    public VarScope(VarScope parent, int id, boolean isFunctionBorder, boolean isLoop) {
        this.parent = parent;
        this.id = id;
        this.isFunctionBorder = isFunctionBorder;
        this.isLoop = isLoop;
        if (parent == null || isFunctionBorder) {
            baseIdx = 0;
        } else {
            baseIdx = parent.baseIdx + parent.names.size();
        }
        captured = isFunctionBorder ? new LinkedHashMap<>() : null;
    }

    public VarScope exitScope() {
        return parent;
    }

    public SpecificVarInfo add(Token ident, boolean isConst, boolean isClosable) {
        if (names.containsKey(ident)) {
            return null;
        }
        containsClosable |= isClosable;
        VarInfo info = new VarInfo(ident.pos().sourcePt(), baseIdx + names.size(), isConst, isClosable)
        names.put(ident.stVal(), info);
        return new SpecificVarInfo(info, -1);
    }

    public SpecificVarInfo get(String ident, boolean crossedFunctionBorder) {
        var rVal = names.get(ident);
        if (rVal == null) {
            // not in this scope
            if (parent == null) {
                return null;
            }
            var sInfo = parent.get(ident, crossedFunctionBorder || isFunctionBorder);
            if (isFunctionBorder) {
                // capture from outside this function
                int cIdx = captured.computeIfAbsent(sInfo.baseInfo(), k -> captured.size());
                // wrap into new specific var info to signal presence of capture
                sInfo = new SpecificVarInfo(sInfo.baseInfo(), cIdx);
            }
            return sInfo;
        } else {
            if (crossedFunctionBorder) {
                rVal.setInClosure();
            }
            // var was defined in this scope
            return new SpecificVarInfo(rVal, -1);
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
            int curDepthIdx = defVars.length - 1;
            VarInfo[] curScopeVars = defVars[curDepthIdx];
            for (GotoNode gtn : toFix) {
                VarInfo[] gtVarsInCurScope = gtn.definedVars[curDepthIdx];
                if (curScopeVars.length > gtVarsInCurScope.length) {
                    // the only way this forward jump is valid is, if this label is at the end of the block,
                    // but we don't know that yet, so we mark this goto as potentially problematic
                    lNode.setMaybeInvalid(gtn, curScopeVars[gtVarsInCurScope.length]);
                    // this goto needs to close all open variables from inner scopes AND THE CURRENT SCOPE
                    gtn.toClose = getClosablesForFixup(gtn, curDepthIdx, true);
                } else {
                    // this jump may need to close variables from inner scopes, BUT NOT THE CURRENT SCOPE
                    gtn.toClose = getClosablesForFixup(gtn, curDepthIdx, false);
                }
                gtn.label = info;
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
        if (rVal != null) {
            rVal.isUsed = true;
        }
        return rVal;
    }

    public VarScope getNextLoopScope() {
        if (isLoop) {
            return this;
        } else if (!isFunctionBorder && parent != null) {
            return parent.getNextLoopScope();
        }
        return null;
    }

    public String toFullString() {
        return "VarScope {parent=%s, id=%s, funcBorder=%s, closable=%s, names=%s, children=%s}".formatted(
                parent == null ? -1 : parent.id, id, isFunctionBorder, containsClosable, names.toString(),
                "[" + children.stream().map(VarScope::toFullString).collect(Collectors.joining(",\n")) + "]");
    }

    private static VarInfo[] getClosablesForFixup(GotoNode gtn, int curDIdx, boolean includeCurrent) {
        var closableList = new ArrayList<VarInfo>();
        for (int scp = gtn.definedVars.length - 1; includeCurrent ? scp >= curDIdx : scp > curDIdx; scp--) {
            for (int vIdx = gtn.definedVars[scp].length - 1; vIdx >= 0; vIdx--) {
                var cVar = gtn.definedVars[scp][vIdx];
                if (cVar.isClosable()) {
                    closableList.add(cVar);
                }
            }
        }
        return closableList.toArray(VarInfo[]::new);
    }

    public int getClosableCount() {
        if (!containsClosable) {
            return 0;
        }
        int cnt = 0;
        for (var v : names.values()) {
            if (v.isClosable()) {
                cnt++;
            }
        }
        return cnt;
    }
}
