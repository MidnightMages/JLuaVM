package dev.asdf00.jluavm.types;

public class LuaNil$ extends LuaVariable${
    public LuaNil$() { // maybe this should be represented as null instead?
        super(LuaType.NIL);
    }

    public static final LuaNil$ singleton = new LuaNil$();
}
