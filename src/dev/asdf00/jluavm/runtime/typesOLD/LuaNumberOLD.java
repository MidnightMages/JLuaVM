package dev.asdf00.jluavm.runtime.typesOLD;

public class LuaNumberOLD extends LuaVariableOLD {
    double value = 1;

    public LuaNumberOLD(double value) {
        super(LuaType.NUM);
        this.value = value;
    }

    public static LuaNumberOLD of(double value) {
        return new LuaNumberOLD(value);
    }

    public double getValue(){
        return value;
    }

    public LuaNumberOLD add(LuaNumberOLD y) {
        return new LuaNumberOLD(value + y.value);
    }

    public LuaNumberOLD sub(LuaNumberOLD y) {
        return new LuaNumberOLD(value - y.value);
    }

    public LuaNumberOLD mul(LuaNumberOLD y) {
        return new LuaNumberOLD(value * y.value);
    }

    public LuaNumberOLD div(LuaNumberOLD y) {
        return new LuaNumberOLD(value / y.value);
    }

    public LuaNumberOLD idiv(LuaNumberOLD y) { // a//b == floor((float)a/(float)b) for pos and negative
        return new LuaNumberOLD(Math.floor(value / y.value));
    }

    public LuaNumberOLD mod(LuaNumberOLD y) {
        return new LuaNumberOLD(value / y.value);
    }

    public LuaNumberOLD pow(LuaNumberOLD y) {
        return new LuaNumberOLD(Math.pow(value, y.value));
    }

    public LuaVariableOLD unm() {
        return new LuaNumberOLD(-value);
    }

    public boolean numEquals(LuaNumberOLD y) {
        return value == y.value;
    }

    public boolean lt(LuaNumberOLD y) {
        return value < y.value;
    }

    public boolean le(LuaNumberOLD y) {
        return value <= y.value;
    }
}
