package dev.asdf00.jluavm.types;

public class LuaNumberBw$ extends LuaVariable$ {
    int value = 1;

    public LuaNumberBw$(int value) {
        super(LuaType.NUM_BW);
        this.value = value;
    }

    public LuaNumberBw$ bor(LuaNumberBw$ y) {
        return new LuaNumberBw$(value | y.value);
    }
    public LuaNumberBw$ bxor(LuaNumberBw$ y) {
        return new LuaNumberBw$(value ^ y.value);
    }
    public LuaNumberBw$ band(LuaNumberBw$ y) {
        return new LuaNumberBw$(value & y.value);
    }
    public LuaNumberBw$ shl(LuaNumberBw$ y) {
        return new LuaNumberBw$(value << y.value); // todo logical vs arithmetic shift?
    }
    public LuaNumberBw$ shr(LuaNumberBw$ y) {
        return new LuaNumberBw$(value >> y.value);
    }

    public LuaVariable$ bnot() {
        return new LuaNumberBw$(~value);
    }

    public boolean numBwEquals(LuaNumberBw$ y) {
        return value == y.value;
    }
}
