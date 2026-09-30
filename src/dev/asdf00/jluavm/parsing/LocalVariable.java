package dev.asdf00.jluavm.parsing;

import dev.asdf00.jluavm.exceptions.loading.LuaParserException;
import dev.asdf00.jluavm.parsing.container.Position;

public class LocalVariable {
    public final String name;
    public final boolean isDeclaredConstant;
    public final boolean isClosable;
    public int argIdx;
    int writtenCnt;
    boolean isCaptured;
    private int positionInFrame;

    LocalVariable(String name, int attributes) {
        this.name = name;
        this.isDeclaredConstant = (attributes & 1) == 1;
        this.isClosable = (attributes & 2) == 2;
        argIdx = -1;
        isCaptured = false;
        writtenCnt = 0;
    }

    public void setWritten(Position pos) {
        if (writtenCnt > 0 && isDeclaredConstant) {
            throw new LuaParserException(pos, "Constant variable must not be written");
        }
        writtenCnt++;
    }

    public boolean isConstant() {
        return writtenCnt <= 1;
    }

    public boolean isBoxed() {
        return isCaptured && !isConstant();
    }

    public void setPositionInFrame(int positionInFrame) {
        this.positionInFrame = positionInFrame;
    }

    public int getPositionInFrame() {
        return positionInFrame;
    }

    public class LocalVariableAccessor implements Cloneable {
        int upScopeDist = 0;
        int captureIdx = -1;

        public boolean isUpvalue() {
            return upScopeDist > 0;
        }

        public int getCaptureIdx() {
            return captureIdx;
        }

        public boolean isArgument() {
            return !isUpvalue() && writtenCnt == 0 && argIdx >= 0;
        }

        public LocalVariable getVariable() {
            return LocalVariable.this;
        }

        @Override
        protected LocalVariableAccessor clone() {
            try {
                return (LocalVariableAccessor) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}