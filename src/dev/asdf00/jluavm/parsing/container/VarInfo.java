package dev.asdf00.jluavm.parsing.container;

public class VarInfo {
    private static final int IS_GLOBAL = 1;
    private static final int CONSTANT = 2;
    private static final int CLOSABLE = 4;
    private static final int IN_CLOSURE = 8;
    private static final int WRITTEN = 16;
    public final String jName;
    private int flags;
    public final int sourcePt;
    public int lastUsedPt;

    public VarInfo(int sourcePt, String jName, boolean isConst, boolean isClosable) {
        this.jName = jName;
        this.sourcePt = sourcePt;
        flags = 0;
        if (isConst) {
            flags |= CONSTANT;
        }
        if (isClosable) {
            flags |= CLOSABLE;
        }
    }

    public boolean isGlobal() {
        return (flags & IS_GLOBAL) != 0;
    }

    public boolean isConstant() {
        return (flags & CONSTANT) != 0;
    }

    public boolean isClosable() {
        return (flags & CLOSABLE) != 0;
    }

    public boolean isInClosure() {
        return (flags & IN_CLOSURE) != 0;
    }

    public boolean isWritten() {
        return (flags & WRITTEN) != 0;
    }

    public void setInClosure() {
        flags |= IN_CLOSURE;
    }

    public void setWritten() {
        flags |= WRITTEN;
    }

    public boolean sitsInBox() {
        return isInClosure() && isWritten();
    }

    @Override
    public String toString() {
        return "VarInfo{" +
                "jName='" + jName + '\'' +
                ", isGlobal=" + isGlobal() +
                ", isConstant=" + isConstant() +
                ", isClosable=" + isClosable() +
                ", isInClosure=" + isInClosure() +
                ", isWritten=" + isWritten() +
                '}';
    }
}
