package dev.asdf00.jluavm.runtime.typesOLD;

public class LuaBooleanOLD extends LuaVariableOLD {
    boolean value;
    public static final LuaBooleanOLD FALSE = new LuaBooleanOLD(false);
    public static final LuaBooleanOLD TRUE = new LuaBooleanOLD(true);

    private LuaBooleanOLD(boolean value) {
        super(LuaType.BOOL);
        this.value = value;
    }

    public boolean getValue(){
        return value;
    }

    public LuaBooleanOLD negated() {
        return value ? FALSE : TRUE;
    }

    public static LuaBooleanOLD fromState(boolean state){
        return state ? TRUE : FALSE;
    }
}
