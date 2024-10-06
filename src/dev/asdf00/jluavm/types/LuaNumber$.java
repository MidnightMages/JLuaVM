package dev.asdf00.jluavm.types;

public class LuaNumber$ extends LuaVariable$ {
    double value = 1;

    public LuaNumber$(double value) {
        super(LuaType.NUM);
        this.value = value;
    }

    public LuaNumber$ add(LuaNumber$ y) {
        return new LuaNumber$(value + y.value);
    }

    public LuaNumber$ sub(LuaNumber$ y) {
        return new LuaNumber$(value - y.value);
    }

    public LuaNumber$ mul(LuaNumber$ y) {
        return new LuaNumber$(value * y.value);
    }

    public LuaNumber$ div(LuaNumber$ y) {
        return new LuaNumber$(value / y.value);
    }

    public LuaNumber$ idiv(LuaNumber$ y) { // a//b == floor((float)a/(float)b) for pos and negative
        return new LuaNumber$(Math.floor(value / y.value));
    }

    public LuaNumber$ mod(LuaNumber$ y) {
        return new LuaNumber$(value / y.value);
    }

    public LuaNumber$ pow(LuaNumber$ y) {
        return new LuaNumber$(Math.pow(value, y.value));
    }

    public LuaVariable$ unm() {
        return new LuaNumber$(-value);
    }
}
