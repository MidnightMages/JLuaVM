package dev.asdf00.jluavm.runtime.types;

import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;
import dev.asdf00.jluavm.exceptions.loading.InternalLuaLexerError;

import static dev.asdf00.jluavm.parsing.Lexer.isDecDigit;
import static dev.asdf00.jluavm.parsing.Lexer.parseHexDouble;

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
    public double dVal; // maybe inline that into lVal with Double.doubleToRawLongBits(dVal) if we have excessive ram use
    public long lVal;
    public final int type; // one-hot encoding
    public int markWord; // currently unused but free due to object alignment

    private LuaObject(Object refVal, double dVal, long lVal, int type) {
        this.refVal = refVal;
        this.dVal = dVal;
        this.lVal = lVal;
        this.type = type;
        this.markWord = 0;
        this.metaTable = null;
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
    // lua object interactions
    // =================================================================================================================

    public LuaObject add(LuaObject other) {
        assert isType(Types.ARITHMETIC) && other.isType(Types.ARITHMETIC);
        boolean doubleCalc;
        double dx = dVal;
        long lx = lVal;
        if (isString()) {
            var cres = coerceToNumber();
            if (cres == null) {
                throw new InternalLuaRuntimeError("relying on number coersion of uncoercable string '%s'".formatted(getString()));
            }
            doubleCalc = cres.isDouble;
            dx = cres.dVal;
            lx = cres.lVal;
        } else if (isDouble()) {
            doubleCalc = true;
        } else {
            assert isLong();
            doubleCalc = false;
        }
        double dy = other.dVal;
        long ly = other.lVal;
        if (other.isString()) {
            var cres = other.coerceToNumber();
            if (cres == null) {
                throw new InternalLuaRuntimeError("relying on number coersion of uncoercable string '%s'".formatted(other.getString()));
            }
            if (!doubleCalc && cres.isDouble) {
                dx = lx;
                doubleCalc = true;
            }
            dy = cres.dVal;
            ly = cres.lVal;
        } else if (other.isDouble()) {
            if (!doubleCalc) {
                dx = lx;
                doubleCalc = true;
            }
        } else {
            assert other.isLong();
            if (doubleCalc) {
                dy = ly;
            }
        }
        return doubleCalc ? LuaObject.of(dx + dy) : LuaObject.of(lx + ly);
    }

    public LuaObject pow(LuaObject other) {
        assert isType(Types.ARITHMETIC) && other.isType(Types.ARITHMETIC);
        // TODO: always coerce to double not long!
        return null;
    }

    public LuaObject concat(LuaObject other) {
        assert isType(Types.ARITHMETIC) && other.isType(Types.ARITHMETIC);
        // TODO: always coerce to string then concat
        return null;
    }

    public boolean hasKey(LuaObject key) {
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
    // coerse to number
    // =================================================================================================================

    /**
     * This method is directly taken from {@linkplain dev.asdf00.jluavm.parsing.Lexer}.
     * TODO: correctly coerce Long.MIN_VAL to long instead of double
     */
    private CoercedString coerceToNumber() {
        assert isString() : "trying to coerce non-string";
        if ((markWord & 0b11) != 0) {
            // already coerced this string, only read cached result
            return new CoercedString((markWord & 1) != 0, dVal, lVal);
        }

        final String stVal = (String) refVal;

        char[] cur = new char[1];
        char[] la = new char[1];

        Runnable advance = new Runnable() {
            private final int len = stVal.length();
            private int i = 0;

            {
                cur[0] = i < len ? stVal.charAt(i) : (char) -1;
                i++;
                la[0] = i < len ? stVal.charAt(i) : (char) -1;
                i++;
            }

            @Override
            public void run() {
                cur[0] = la[0];
                la[0] = i < len ? stVal.charAt(i) : (char) -1;
                i++;
            }
        };

        // skip whitespace
        while (Character.isWhitespace(cur[0])) {
            advance.run();
        }

        boolean positive = true;
        if (cur[0] == '-') {
            positive = false;
            advance.run();
        }

        if (!isDecDigit(cur[0]) && !(cur[0] == '.' && isDecDigit(la[0]))) {
            // fail to coerce
            return null;
        }

        boolean isInteger = true;
        boolean isHex = false;
        boolean isExp = false;
        boolean isValid = true;
        boolean allowPM = false;
        final var nb = new StringBuilder();
        nb.append(cur);
        if (cur[0] == '0') {
            advance.run();
            if (cur[0] == 'x' || cur[0] == 'X') {
                isHex = true;
                isValid = false;
                nb.append(cur);
                advance.run();
            }
        } else if (cur[0] != '.') {
            advance.run();
        }

        Runnable step = () -> {
            nb.append(cur[0]);
            advance.run();
        };
        for (; ; step.run()) {
            if (allowPM) {
                allowPM = false;
                isExp = true;
                if (cur[0] == '+' || cur[0] == '-') {
                    isValid = false;
                    continue;
                }
            }
            if (isDecDigit(cur[0])) {
                isValid = true;
                continue;
            }
            if (isHex) {
                if (('a' <= cur[0] && cur[0] <= 'f') || ('A' <= cur[0] && cur[0] <= 'F')) {
                    continue;
                }
            }
            if (isInteger) {
                if (cur[0] == '.') {
                    isInteger = false;
                    isValid = false;
                    isHex = false;
                    continue;
                }
            } else if (!isExp) {
                // exponent?
                if (!isValid) {
                    // fail to coerce
                    return null;
                }
                if (cur[0] == 'p' || cur[0] == 'P' || cur[0] == 'e' || cur[0] == 'E') {
                    isInteger = false;
                    isHex = false;
                    allowPM = true;
                    isValid = false;
                    continue;
                }
            }
            break;
        }

        String number = nb.toString();
        if (!isValid) {
            // fail to coerce
            return null;
        }
        try {
            double doubleValue = -1;
            long longValue = -1;
            if (number.startsWith("0x")) {
                if (isInteger) {
                    doubleValue = parseHexDouble(number.substring(2));
                    if (doubleValue <= Long.MAX_VALUE) {
                        doubleValue = -1;
                        longValue = Long.parseLong(number.substring(2), 16);
                    }
                } else {
                    int point = number.indexOf('.');
                    int ppos = number.indexOf('p');
                    if (ppos < 0) {
                        ppos = number.indexOf('P');
                    }
                    int epos = number.indexOf('e');
                    if (epos < 0) {
                        epos = number.indexOf('E');
                    }
                    if (ppos < 0 && epos < 0) {
                        doubleValue = parseHexDouble(number.substring(2, point)) + Double.parseDouble(number.substring(point));
                    } else {
                        double a = epos < 0 ? 2 : 10;
                        int splitter = epos < 0 ? ppos : epos;
                        doubleValue = parseHexDouble(number.substring(2, point)) + Double.parseDouble(number.substring(point, splitter))
                                * Math.pow(a, Double.parseDouble(number.substring(splitter + 1)));
                    }
                }
            } else {
                if (isInteger) {
                    doubleValue = Double.parseDouble(number);
                    if (doubleValue <= Long.MAX_VALUE) {
                        doubleValue = -1;
                        longValue = Long.parseLong(number);
                    }
                } else {
                    int ppos = number.indexOf('p');
                    if (ppos < 0) {
                        ppos = number.indexOf('P');
                    }
                    int epos = number.indexOf('e');
                    if (epos < 0) {
                        epos = number.indexOf('E');
                    }
                    if (ppos < 0 && epos < 0) {
                        doubleValue = Double.parseDouble(number);
                    } else {
                        double a = epos < 0 ? 2 : 10;
                        int splitter = epos < 0 ? ppos : epos;
                        doubleValue = Double.parseDouble(number.substring(0, splitter)) * Math.pow(a, Double.parseDouble(number.substring(splitter + 1)));
                    }
                }
            }
            if (!positive) {
                if (isInteger) {
                    longValue = -longValue;
                } else {
                    doubleValue = -doubleValue;
                }
            }
            // save result to cache
            markWord |= 1 << (isInteger ? 1 : 0);
            if (isInteger) {
                markWord |= 0b10;
                lVal = longValue;
            } else {
                markWord |= 1;
                dVal = doubleValue;
            }
            return new CoercedString(!isInteger, doubleValue, longValue);
        } catch (NumberFormatException e) {
            throw new InternalLuaLexerError("Unexpected failure while reading number '%s'".formatted(number), e);
        }
    }

    private record CoercedString(boolean isDouble, double dVal, long lVal) {

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
            throw new InternalLuaRuntimeError("This is not an array!");
        }
        return (LuaObject[]) refVal;
    }

    public boolean getBool() {
        assert isBoolean();
        return lVal != 0;
    }

    public String getString() {
        assert isString();
        return (String) refVal;
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

    public boolean isIntCoercible() {
        return isLong() || (isDouble() && (double)((long) dVal) == dVal);
    }

    public boolean isNumberCoercible() {
        if (isNumber()) {
            return true;
        } else if (isString() && coerceToNumber() != null) {
            return true;
        } else {
            return false;
        }
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

    public static LuaObject table(LuaObject... val) {

        return new LuaObject(val, 0, 0, Types.TABLE);
    }
}
