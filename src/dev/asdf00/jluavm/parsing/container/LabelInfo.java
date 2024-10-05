package dev.asdf00.jluavm.parsing.container;

public class LabelInfo {
    public final Position pos;
    public final VarInfo[][] definedVars;
    public final int scopeId;
    public final int jmpNo;
    public final boolean isInLoopScope;
    public boolean isLast;
    public boolean isUsed;

    public LabelInfo(Position pos, VarInfo[][] definedVars, int scopeId, int jmpNo, boolean isInLoopScope) {
        this.pos = pos;
        this.definedVars = definedVars;
        this.scopeId = scopeId;
        this.jmpNo = jmpNo;
        this.isInLoopScope = isInLoopScope;
        isLast = true;
        isUsed = false;
    }

    public boolean canBeContinue() {
        return isInLoopScope && isLast;
    }
}
