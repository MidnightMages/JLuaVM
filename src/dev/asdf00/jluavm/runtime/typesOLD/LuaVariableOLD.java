package dev.asdf00.jluavm.runtime.typesOLD;

import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;

import java.util.Map;

/**
 * TODO: make this class an interface, start all functions with "_lua" since they might leak out of this package via userdata.
 */
public abstract class LuaVariableOLD {

    private final LuaType varKind;

    public LuaVariableOLD(LuaType varKind) {
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
        NUM_BW("number"),
        FUNCTION_RESULT("no-type");

        public final String fancyName;

        LuaType(String fancyName) {
            this.fancyName = fancyName;
        }

        private static final Map<Class<? extends LuaVariableOLD>, LuaType> clazzMap = Map.of(
                LuaBooleanOLD.class, LuaVariableOLD.LuaType.BOOL,
                LuaFunctionOLD.class, LuaVariableOLD.LuaType.FUNC,
                LuaNilOLD.class, LuaVariableOLD.LuaType.NIL,
                LuaNumberOLD.class, LuaVariableOLD.LuaType.NUM,
                LuaNumberBwOLD.class, LuaVariableOLD.LuaType.NUM_BW,
                LuaStringOLD.class, LuaVariableOLD.LuaType.STR,
                LuaTableOLD.class, LuaVariableOLD.LuaType.TABLE);

        public static LuaType fromClass(Class<? extends LuaVariableOLD> type) {
            var res = clazzMap.get(type);
            if (res == null) {
                throw new InternalLuaRuntimeError("no type for unknown lua type class " + type.getName());
            }
            return res;
        }
    }
}
