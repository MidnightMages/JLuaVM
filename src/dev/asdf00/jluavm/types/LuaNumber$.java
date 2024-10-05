package dev.asdf00.jluavm.types;

public class LuaNumber$ extends LuaVariable$ {
    double value = 1;

    public LuaNumber$(double value) {
        super(LuaType.NUM);
        this.value = value;
    }

    public LuaNumber$ divide(LuaNumber$ y) {
        return new LuaNumber$(value / y.value);
    }
}
