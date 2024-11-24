package dev.asdf00.jluavm.parsing.container;

import dev.asdf00.jluavm.exceptions.loading.LuaSemanticException;
import dev.asdf00.jluavm.parsing.ir.controlflow.GotoNode;
import dev.asdf00.jluavm.parsing.ir.controlflow.LabelNode;
import dev.asdf00.jluavm.utils.Tuple;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

public class VarScope {
    public static final VarScope EMPTY_DUMMY = new VarScope(null, -1, false, false, false, false);

    public final VarScope parent;
    private final int baseIdx;
    public final ArrayList<VarScope> children = new ArrayList<>();
    public final int id;

    private final LinkedHashMap<String, VarInfo> names = new LinkedHashMap<>();
    private final LinkedHashMap<String, LabelInfo> labels = new LinkedHashMap<>();

    public final boolean isFunctionBorder;
    public final boolean hasParamsArg;
    public final boolean isLoop;
    public final boolean isInlined;
    private int closableCnt;

    public final LinkedHashMap<SpecificVarInfo, Integer> captured;

    public VarScope(VarScope parent, int id, boolean isFunctionBorder, boolean hasParamsArg, boolean isLoop, boolean isInlined) {
        this.parent = parent;
        this.id = id;
        this.isFunctionBorder = isFunctionBorder;
        this.hasParamsArg = hasParamsArg;
        this.isLoop = isLoop;
        this.isInlined = isInlined;
        if (parent == null || isFunctionBorder) {
            baseIdx = 0;
        } else {
            baseIdx = parent.baseIdx + parent.names.size();
        }
        closableCnt = 0;
        captured = isFunctionBorder ? new LinkedHashMap<>() : null;
    }

    public SpecificVarInfo add(Token ident, boolean isConst, boolean isClosable) {
        if (names.containsKey(ident)) {
            return null;
        }
        if (isClosable) {
            closableCnt++;
        }
        VarInfo info = new VarInfo(ident.pos(), ident.stVal(), baseIdx + names.size(), isConst, isClosable);
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
            if (sInfo == null) {
                return null;
            }
            if (isFunctionBorder) {
                // capture from outside this function
                int cIdx = captured.computeIfAbsent(sInfo, k -> captured.size());
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

    public LabelInfo addLabel(Token ident, int[] localsCnt, int[] closableCnt) {
        boolean notAllowed = false;
        var cur = this;
        while (!cur.isFunctionBorder && !notAllowed) {
            notAllowed |= cur.labels.containsKey(ident.stVal());
            cur = cur.parent;
        }
        notAllowed |= cur.labels.containsKey(ident.stVal());
        if (notAllowed) {
            throw new LuaSemanticException(ident.pos(), "Label '%s' defined twice".formatted(ident.stVal()));
        }
        var info = new LabelInfo(ident.stVal(), localsCnt, closableCnt);
        labels.put(ident.stVal(), info);
        return info;
    }

    public LabelInfo getLabel(String ident) {
        // upwards search until function border
        var rVal = labels.get(ident);
        if (rVal == null && !isFunctionBorder && parent != null) {
            rVal = parent.getLabel(ident);
        }
        return rVal;
    }

    public String toFullString() {
        return "VarScope {parent=%s, id=%s, funcBorder=%s, closable=%s, names=%s, children=%s}".formatted(
                parent == null ? -1 : parent.id, id, isFunctionBorder, closableCnt, names.toString(),
                "[" + children.stream().map(VarScope::toFullString).collect(Collectors.joining(",\n")) + "]");
    }

    public int getClosableCount() {
        return closableCnt;
    }

    public int getLocalsCount() {
        return names.size();
    }
}
