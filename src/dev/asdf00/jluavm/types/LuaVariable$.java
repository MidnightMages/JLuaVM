package dev.asdf00.jluavm.types;

import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;

import java.util.Map;

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

    public boolean isBoolean() {
        return isType(LuaType.BOOL);
    }

    public boolean isNumber() {
        return isType(LuaType.NUM);
    }

    public boolean isNil() {
        return isType(LuaType.NIL);
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

        private static final Map<Class<? extends LuaVariable$>, LuaType> clazzMap = Map.of(
                LuaBoolean$.class, LuaVariable$.LuaType.BOOL,
                LuaFunction$.class, LuaVariable$.LuaType.FUNC,
                LuaNil$.class, LuaVariable$.LuaType.NIL,
                LuaNumber$.class, LuaVariable$.LuaType.NUM,
                LuaNumberBw$.class, LuaVariable$.LuaType.NUM_BW,
                LuaString$.class, LuaVariable$.LuaType.STR,
                LuaTable$.class, LuaVariable$.LuaType.TABLE);

        public static LuaType fromClass(Class<? extends LuaVariable$> type) {
            var res = clazzMap.get(type);
            if (res == null) {
                throw new InternalLuaRuntimeError("no type for unknown lua type class " + type.getName());
            }
            return res;
        }
    }
}
