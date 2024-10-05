package dev.asdf00.jluavm.types;

public class LuaString$ extends LuaVariable$ {
    private final String funcName;

    public LuaString$(String funcName) {
        super(LuaType.STR);
        this.funcName = funcName;
    }
}
