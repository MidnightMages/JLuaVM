package dev.asdf00.jluavm.parsing.container;

import dev.asdf00.jluavm.exceptions.loading.LuaSemanticException;
import dev.asdf00.jluavm.utils.Tuple;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

public class VarScope {
    public static final VarScope EMPTY_DUMMY = new VarScope(null, -1, false, false, false, false);

    public final VarScope parent;
    private final int baseIdx;
    public final ArrayList<VarScope> children = new ArrayList<>();
    public final int id;

    private final List<Tuple<String, VarInfo>> names = new ArrayList<>();
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
        if (isClosable) {
            closableCnt++;
        }
        VarInfo info = new VarInfo(ident.pos(), ident.stVal(), baseIdx + names.size(), isConst, isClosable);
        names.add(new Tuple<>(ident.stVal(), info));
        return new SpecificVarInfo(info, -1);
    }

    public SpecificVarInfo get(String ident, boolean crossedFunctionBorder) {
        VarInfo rVal = null;
        for (int i = names.size()-1; i >= 0; i--) {
            var e = names.get(i);
            if (e.x().equals(ident)) {
                rVal = e.y();
                break;
            }
        }
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
            notAllowed = cur.labels.containsKey(ident.stVal());
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

    @SuppressWarnings("unused")
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

    public int getPrevFunctionLocalsCount() {
        var cur = this;
        int locals = 0;
        while (!cur.isFunctionBorder) {
            locals += cur.getLocalsCount();
            cur = cur.parent;
        }
        locals += cur.getLocalsCount();
        return locals - getLocalsCount();
    }

    public int[] getBoxedParameterIndices() {
        assert isFunctionBorder;
        var rv = new ArrayList<Integer>();
        int i = 0;
        for (var e : names) {
            if (e.y().sitsInBox()) {
                rv.add(i);
            }
            i++;
        }
        return rv.stream().mapToInt(x -> x).toArray();
    }
}
