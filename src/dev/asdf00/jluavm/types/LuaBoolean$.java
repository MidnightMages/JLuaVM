package dev.asdf00.jluavm.types;

public class LuaBoolean$ extends LuaVariable$ {
    boolean value;

    public LuaBoolean$(boolean value) {
        super(LuaType.BOOL);
        this.value = value;
    }

    public boolean getValue(){
        return value;
    }
}
