package dev.asdf00.jluavm.runtime.typesOLD;

public class LuaNumberBwOLD extends LuaVariableOLD {
    long value = 1;

    public LuaNumberBwOLD(long value) {
        super(LuaType.NUM_BW);
        this.value = value;
    }

    public static LuaNumberBwOLD of(long value) {
        return new LuaNumberBwOLD(value);
    }

    public LuaNumberBwOLD bor(LuaNumberBwOLD y) {
        return new LuaNumberBwOLD(value | y.value);
    }
    public LuaNumberBwOLD bxor(LuaNumberBwOLD y) {
        return new LuaNumberBwOLD(value ^ y.value);
    }
    public LuaNumberBwOLD band(LuaNumberBwOLD y) {
        return new LuaNumberBwOLD(value & y.value);
    }
    public LuaNumberBwOLD shl(LuaNumberBwOLD y) {
        return new LuaNumberBwOLD(value << y.value); // todo logical vs arithmetic shift?
    }
    public LuaNumberBwOLD shr(LuaNumberBwOLD y) {
        return new LuaNumberBwOLD(value >> y.value);
    }

    public LuaVariableOLD bnot() {
        return new LuaNumberBwOLD(~value);
    }

    public boolean numBwEquals(LuaNumberBwOLD y) {
        return value == y.value;
    }
}
