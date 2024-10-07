package dev.asdf00.jluavm.types;

public class LuaBoolean$ extends LuaVariable$ {
    boolean value;
    public static final LuaBoolean$ FALSE = new LuaBoolean$(false);
    public static final LuaBoolean$ TRUE = new LuaBoolean$(true);

    private LuaBoolean$(boolean value) {
        super(LuaType.BOOL);
        this.value = value;
    }

    public boolean getValue(){
        return value;
    }

    public LuaBoolean$ negated() {
        return value ? FALSE : TRUE;
    }

    public static LuaBoolean$ fromState(boolean state){
        return state ? TRUE : FALSE;
    }
}
