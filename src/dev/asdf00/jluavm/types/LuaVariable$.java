package dev.asdf00.jluavm.types;

public abstract class LuaVariable$ {

    private final LuaType varKind;

    public LuaVariable$(LuaType varKind) {
        this.varKind = varKind;
    }

    public boolean isType(LuaType kind){
        return kind.equals(this.varKind);
    }

    public boolean isTable(){
        return isType(LuaType.TABLE);
    }

    protected boolean isFunction() {
        return isType(LuaType.FUNC);
    }

    public boolean isNumber() {
        return isType(LuaType.NUM);
    }

    public boolean isNumberBw() {
        return isType(LuaType.NUM_BW); // TODO maybe also add some more conditions
    }

    public boolean isString() {
        return isType(LuaType.STR);
    }

    public LuaType getType() {
        return varKind;
    }

    public enum LuaType {
        NIL("nil"),
        BOOL("boolean"),
        NUM("number"),
        STR("string"),
        FUNC("function"),
        TABLE("table"),
        UDATA("userdata"),
        THREAD("thread"),
        NUM_BW("number");

        public final String fancyName;

        LuaType(String fancyName) {
            this.fancyName = fancyName;
        }
    }
}
