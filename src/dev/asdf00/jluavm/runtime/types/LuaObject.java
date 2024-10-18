package dev.asdf00.jluavm.runtime.types;

import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;

public final class LuaObject {
    public static final LuaObject NIL = new LuaObject(null, 0, 0, Types.NIL);
    public static final LuaObject FALSE = new LuaObject(null, 0, 0, Types.BOOLEAN);
    public static final LuaObject TRUE = new LuaObject(null, 1, 0, Types.BOOLEAN);

    public static final class Types {
        // plain types
        public static final int NIL = 1 << 0;
        public static final int BOOLEAN = 1 << 1;
        public static final int DOUBLE = 1 << 2;
        public static final int LONG = 1 << 3;
        public static final int STRING = 1 << 4;
        public static final int FUNCTION = 1 << 5;
        public static final int USERDATA = 1 << 6;
        public static final int THREAD = 1 << 7;
        public static final int TABLE = 1 << 8;
        public static final int ARRAY = 1 << 9;
        public static final int BOX = 1 << 10;
        // combinations
        public static final int NUMBER = DOUBLE | LONG;
        public static final int ARITHMETIC = DOUBLE | LONG | STRING;
    }

    public Object refVal; // reassignable only for box
    public LuaObject metaTable;
    public final double dVal; // maybe inline that into lVal with Double.doubleToRawLongBits(dVal) if we have excessive ram use
    public final long lVal;
    public final int type; // one-hot encoding
    public final int otherMarks; // currently unused but free due to object alignment

    private LuaObject(Object refVal, double dVal, long lVal, int type) {
        this.refVal = refVal;
        this.dVal = dVal;
        this.lVal = lVal;
        this.type = type;
        this.otherMarks = 0;
        this.metaTable = null;
    }

    // =================================================================================================================
    // construction methods
    // =================================================================================================================

    public static LuaObject box() {
        return new LuaObject(null, 0, 0, Types.BOX);
    }

    public static LuaObject nil() {
        return NIL;
    }

    public static LuaObject of(boolean val) {
        return val ? TRUE : FALSE;
    }

    public static LuaObject of(double val) {
        return new LuaObject(null, val, 0, Types.DOUBLE);
    }

    public static LuaObject of(long val) {
        return new LuaObject(null, 0, val, Types.LONG);
    }

    public static LuaObject of(String val) {
        return new LuaObject(val, 0, 0, Types.STRING);
    }

    public static LuaObject of(LuaFunction val) {
        return new LuaObject(val, 0, 0, Types.FUNCTION);
    }

    public static LuaObject of(ILuaUserData val) {
        return new LuaObject(val, 0, 0, Types.USERDATA);
    }

    public static LuaObject of(LuaObject... val) {
        return new LuaObject(val, 0, 0, Types.ARRAY);
    }



    // =================================================================================================================
    // utility methods
    // =================================================================================================================

    public LuaObject getMetaTable() {
        return metaTable;
    }

    public boolean isType(int types) {
        return (type & types) != 0;
    }

    public int getType() {
        return type;
    }

    public boolean isNaN() {
        return isDouble() && Double.isNaN(dVal);
    }

    // =================================================================================================================
    // getter methods
    // =================================================================================================================

    public LuaFunction getFunc() {
        if (!isFunction()) {
            throw new InternalLuaRuntimeError("This is not a function!");
        }
        return (LuaFunction) refVal;
    }

    public LuaObject[] asArray() {
        if (!isArray()) {
            throw new InternalLuaRuntimeError("This is not a function!");
        }
        return (LuaObject[]) refVal;
    }

    // =================================================================================================================
    // lua object interactions
    // =================================================================================================================

    public LuaObject add(LuaObject other) {
        assert isType(Types.ARITHMETIC) && other.isType(Types.ARITHMETIC);
        return null;
    }

    public boolean hasKey(LuaObject key)  {
        return false;
    }

    public LuaObject get(LuaObject other) {
        return null;
    }

    public LuaObject set(LuaObject key, LuaObject value) {
        return null;
    }

    public void setBox(LuaObject val) {
        if (!isBox()) {
            throw new InternalLuaRuntimeError("This is not a box!");
        }
        refVal = val;
    }

    public LuaObject unbox() {
        if (!isBox()) {
            throw new InternalLuaRuntimeError("This is not a box!");
        }
        return (LuaObject) refVal;
    }

    // =================================================================================================================
    // easy type check methods
    // =================================================================================================================

    public boolean isNil() {
        return isType(Types.NIL);
    }

    public boolean isBoolean() {
        return isType(Types.BOOLEAN);
    }

    public boolean isDouble() {
        return isType(Types.DOUBLE);
    }

    public boolean isLong() {
        return isType(Types.LONG);
    }

    public boolean isString() {
        return isType(Types.STRING);
    }

    public boolean isFunction() {
        return isType(Types.FUNCTION);
    }

    public boolean isUserData() {
        return isType(Types.USERDATA);
    }

    public boolean isThread() {
        return isType(Types.THREAD);
    }

    public boolean isTable() {
        return isType(Types.TABLE);
    }

    public boolean isArray() {
        return isType(Types.ARRAY);
    }

    public boolean isBox() {
        return isType(Types.BOX);
    }

    public boolean isNumber() {
        return isType(Types.NUMBER);
    }

    public boolean isArithmetic() {
        return isType(Types.ARITHMETIC);
    }
}
