package dev.asdf00.jluavm.runtime.typesOLD;

public class LuaNilOLD extends LuaVariableOLD {
    private LuaNilOLD() { // maybe this should be represented as null instead?
        super(LuaType.NIL);
    }

    public static final LuaNilOLD singleton = new LuaNilOLD();
}
