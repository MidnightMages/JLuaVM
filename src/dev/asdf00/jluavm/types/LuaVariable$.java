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

    public enum LuaType {
        NIL("nil"),
        BOOL("boolean"),
        NUM("number"),
        STR("string"),
        FUNC("function"),
        TABLE("table"),
        UDATA("userdata"),
        THREAD("thread");

        public final String fancyName;

        LuaType(String fancyName) {
            this.fancyName = fancyName;
        }
    }
}
