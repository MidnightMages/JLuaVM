package dev.asdf00.jluavm.runtime.types;

import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;
import dev.asdf00.jluavm.exceptions.loading.InternalLuaLexerError;
import dev.asdf00.jluavm.parsing.Lexer;

import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.Base64;

import static dev.asdf00.jluavm.parsing.Lexer.isDecDigit;
import static dev.asdf00.jluavm.parsing.Lexer.parseHexDouble;

@SuppressWarnings("unused")
public final class LuaObject {
    public static final LuaObject NIL = new LuaObject(null, 0, 0, Types.NIL);
    public static final LuaObject FALSE = new LuaObject(null, 0, 0, Types.BOOLEAN);
    public static final LuaObject TRUE = new LuaObject(null, 0, 1, Types.BOOLEAN);

    public static final class Types {
        // plain types
        @SuppressWarnings("PointlessBitwiseExpression")
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

    public void setMetatable(LuaObject mt) {
        assert mt.get(LuaObject.of("__metatable")).isNil();
        metaTable = mt;
    }

    public LuaObject getMetaTableValueOrNil(String mtKey) {
        return metaTable != null ? metaTable.get(LuaObject.of(mtKey)) : NIL;
    }

    public boolean isType(int types) {
        return (type & types) != 0;
    }

    public int getType() {
        return type;
    }

    public String getTypeAsString() {
        return switch (type) {
            case Types.NIL -> "nil";
            case Types.BOOLEAN -> "boolean";
            case Types.DOUBLE, Types.LONG -> "number";
            case Types.STRING -> "string";
            case Types.FUNCTION -> "function";
            case Types.USERDATA -> "userdata";
            case Types.THREAD -> "thread";
            case Types.TABLE, Types.ARRAY -> "table";
            case Types.BOX -> "box<" + ((LuaObject) refVal).getTypeAsString() + ">";
            default -> throw new IllegalStateException("Unexpected case value: " + type);
        };
    }

    public boolean isNaN() {
        return isDouble() && Double.isNaN(dVal);
    }

    public boolean isTruthy() {
        return !isNil() && this != FALSE;
    }

    // =================================================================================================================
    // lua object interactions
    // =================================================================================================================

    // MUST return an integer (caller requirement)
    public LuaObject len() {
        assert isType(Types.STRING) || isType(Types.TABLE);
        if (this.isString())
            return LuaObject.of(getString().length());

        return LuaObject.of(((LuaHashMap) refVal).luaLen());
    }


    public LuaObject unm() {
        assert isType(Types.ARITHMETIC);
        boolean doubleCalc;
        double dx = dVal;
        long lx = lVal;
        if (isString()) {
            var cres = coerceToNumber();
            if (cres == null) {
                throw new InternalLuaRuntimeError("coersion of uncoercable string '%s'".formatted(getString()));
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
        return doubleCalc ? LuaObject.of(-dx) : LuaObject.of(-lx);
    }

    public LuaObject add(LuaObject other) {
        assert isType(Types.ARITHMETIC) && other.isType(Types.ARITHMETIC);
        boolean doubleCalc;
        double dx = dVal;
        long lx = lVal;
        if (isString()) {
            var cres = coerceToNumber();
            if (cres == null) {
                throw new InternalLuaRuntimeError("coersion of uncoercable string '%s'".formatted(getString()));
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
                throw new InternalLuaRuntimeError("coersion of uncoercable string '%s'".formatted(getString()));
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

    public LuaObject sub(LuaObject other) {
        assert isType(Types.ARITHMETIC) && other.isType(Types.ARITHMETIC);
        boolean doubleCalc;
        double dx = dVal;
        long lx = lVal;
        if (isString()) {
            var cres = coerceToNumber();
            if (cres == null) {
                throw new InternalLuaRuntimeError("coersion of uncoercable string '%s'".formatted(getString()));
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
                throw new InternalLuaRuntimeError("coersion of uncoercable string '%s'".formatted(getString()));
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
        return doubleCalc ? LuaObject.of(dx - dy) : LuaObject.of(lx - ly);
    }

    public LuaObject mul(LuaObject other) {
        assert isType(Types.ARITHMETIC) && other.isType(Types.ARITHMETIC);
        boolean doubleCalc;
        double dx = dVal;
        long lx = lVal;
        if (isString()) {
            var cres = coerceToNumber();
            if (cres == null) {
                throw new InternalLuaRuntimeError("coersion of uncoercable string '%s'".formatted(getString()));
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
                throw new InternalLuaRuntimeError("coersion of uncoercable string '%s'".formatted(getString()));
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
        return doubleCalc ? LuaObject.of(dx * dy) : LuaObject.of(lx * ly);
    }

    public LuaObject div(LuaObject other) {
        assert isType(Types.ARITHMETIC) && other.isType(Types.ARITHMETIC);
        double dx;
        if (isString()) {
            var cres = coerceToNumber();
            if (cres == null) {
                throw new InternalLuaRuntimeError("coersion of uncoercable string '%s'".formatted(getString()));
            }
            dx = cres.isDouble ? cres.dVal : cres.lVal;
        } else if (isDouble()) {
            dx = dVal;
        } else {
            assert isLong();
            dx = lVal;
        }
        double dy;
        if (other.isString()) {
            var cres = other.coerceToNumber();
            if (cres == null) {
                throw new InternalLuaRuntimeError("coersion of uncoercable string '%s'".formatted(getString()));
            }
            dy = cres.isDouble ? cres.dVal : cres.lVal;
        } else if (other.isDouble()) {
            dy = other.dVal;
        } else {
            assert other.isLong();
            dy = other.lVal;
        }
        return LuaObject.of(dx / dy);
    }

    public LuaObject idiv(LuaObject other) {
        assert isType(Types.ARITHMETIC) && other.isType(Types.ARITHMETIC);
        boolean doubleCalc;
        double dx = dVal;
        long lx = lVal;
        if (isString()) {
            var cres = coerceToNumber();
            if (cres == null) {
                throw new InternalLuaRuntimeError("coersion of uncoercable string '%s'".formatted(getString()));
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
                throw new InternalLuaRuntimeError("coersion of uncoercable string '%s'".formatted(getString()));
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

        if (doubleCalc){
            var dres = LuaObject.of(Math.floor(dx/dy));
            return dres.hasLongRepr() ? LuaObject.of(dres.asLong()) : dres;
        }
        return LuaObject.of(lx / ly);
    }

    public LuaObject mod(LuaObject other) {
        assert isType(Types.ARITHMETIC) && other.isType(Types.ARITHMETIC);
        boolean doubleCalc;
        double dx = dVal;
        long lx = lVal;
        if (isString()) {
            var cres = coerceToNumber();
            if (cres == null) {
                throw new InternalLuaRuntimeError("coersion of uncoercable string '%s'".formatted(getString()));
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
                throw new InternalLuaRuntimeError("coersion of uncoercable string '%s'".formatted(getString()));
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
        return LuaObject.of(doubleCalc ? (long) (dx % dy) : lx % ly);
    }

    public LuaObject pow(LuaObject other) {
        assert isType(Types.ARITHMETIC) && other.isType(Types.ARITHMETIC);
        double dx;
        if (isString()) {
            var cres = coerceToNumber();
            if (cres == null) {
                throw new InternalLuaRuntimeError("coersion of uncoercable string '%s'".formatted(getString()));
            }
            dx = cres.isDouble ? cres.dVal : cres.lVal;
        } else if (isDouble()) {
            dx = dVal;
        } else {
            assert isLong();
            dx = lVal;
        }
        double dy;
        if (other.isString()) {
            var cres = other.coerceToNumber();
            if (cres == null) {
                throw new InternalLuaRuntimeError("coersion of uncoercable string '%s'".formatted(getString()));
            }
            dy = cres.isDouble ? cres.dVal : cres.lVal;
        } else if (other.isDouble()) {
            dy = other.dVal;
        } else {
            assert other.isLong();
            dy = other.lVal;
        }
        return LuaObject.of(Math.pow(dx, dy));
    }

    private static final DecimalFormat doubleToStringFormat; // TODO add unittest to make sure this lack of threadsafety doesnt cause any issues
    private static final DecimalFormat longToStringFormat = new DecimalFormat("#");

    static {
        var dfs = DecimalFormatSymbols.getInstance();
        dfs.setDecimalSeparator('.');
        doubleToStringFormat = new DecimalFormat("0.0#############", dfs);
    }

    public LuaObject concat(LuaObject other) {
        assert isType(Types.ARITHMETIC) && other.isType(Types.ARITHMETIC);
        String dx;
        if (isString()) {
            dx = getString();
        } else if (isDouble()) {
            dx = doubleToStringFormat.format(dVal);
        } else {
            assert isLong();
            dx = longToStringFormat.format(lVal);
        }
        String dy;
        if (other.isString()) {
            dy = other.getString();
        } else if (other.isDouble()) {
            dy = doubleToStringFormat.format(other.dVal);
        } else {
            assert other.isLong();
            dy = longToStringFormat.format(other.lVal);
        }
        return LuaObject.of(dx + dy);
    }

    public LuaObject band(LuaObject other) {
        assert isIntCoercible();
        assert other.isIntCoercible();

        var lx = isLong() ? lVal : (long) dVal;
        var ly = other.isLong() ? other.lVal : (long) other.dVal;
        return LuaObject.of(lx & ly);
    }

    public LuaObject bor(LuaObject other) {
        assert isIntCoercible();
        assert other.isIntCoercible();

        var lx = isLong() ? lVal : (long) dVal;
        var ly = other.isLong() ? other.lVal : (long) other.dVal;
        return LuaObject.of(lx | ly);
    }

    public LuaObject bxor(LuaObject other) {
        assert isIntCoercible();
        assert other.isIntCoercible();

        var lx = isLong() ? lVal : (long) dVal;
        var ly = other.isLong() ? other.lVal : (long) other.dVal;
        return LuaObject.of(lx ^ ly);
    }

    public LuaObject bnot() {
        assert isIntCoercible();

        return LuaObject.of(~(isLong() ? lVal : (long) dVal));
    }

    public LuaObject shl(LuaObject other) {
        assert isIntCoercible();
        assert other.isIntCoercible();

        var lx = isLong() ? lVal : (long) dVal;
        var ly = other.isLong() ? other.lVal : (long) other.dVal;
        return LuaObject.of(lx << ly);
    }

    public LuaObject shr(LuaObject other) {
        assert isIntCoercible();
        assert other.isIntCoercible();

        var lx = isLong() ? lVal : (long) dVal;
        var ly = other.isLong() ? other.lVal : (long) other.dVal;
        return LuaObject.of(lx >>> ly);
    }

    public LuaObject eq(LuaObject other) {
        // both number or both strings
        assert (isNumber() && other.isNumber() || isString() && other.isString());

        if (isNumber()) {
            if (isLong() && other.isLong())
                return LuaObject.of(lVal == other.lVal);

            var dx = isLong() ? (double) lVal : dVal;
            var dy = other.isLong() ? (double) other.lVal : other.dVal;
            return LuaObject.of(dx == dy);
        }
        return LuaObject.of(getString().equals(other.getString()));
    }

    public LuaObject lt(LuaObject other) {
        // both number or both strings
        assert (isNumber() && other.isNumber() || isString() && other.isString());

        if (isNumber()) {
            if (isLong() && other.isLong())
                return LuaObject.of(lVal < other.lVal);

            var dx = isLong() ? (double) lVal : dVal;
            var dy = other.isLong() ? (double) other.lVal : other.dVal;
            return LuaObject.of(dx < dy);
        }

        var sx = getString();
        var sy = other.getString();
        for (int i = 0; i < Math.min(sx.length(), sy.length()); i++) {
            var cx = sx.charAt(i);
            var cy = sy.charAt(i);
            if (cx < cy)
                return LuaObject.TRUE;
            else if (cx > cy)
                return LuaObject.FALSE;
        }
        return LuaObject.of(sx.length() < sy.length());
    }

    public LuaObject le(LuaObject other) {
        // both number or both strings
        assert (isNumber() && other.isNumber() || isString() && other.isString());

        if (isNumber()) {
            if (isLong() && other.isLong())
                return LuaObject.of(lVal < other.lVal);

            var dx = isLong() ? (double) lVal : dVal;
            var dy = other.isLong() ? (double) other.lVal : other.dVal;
            return LuaObject.of(dx < dy);
        }

        var sx = getString();
        var sy = other.getString();
        for (int i = 0; i < Math.min(sx.length(), sy.length()); i++) {
            var cx = sx.charAt(i);
            var cy = sy.charAt(i);
            if (cx < cy)
                return LuaObject.TRUE;
            else if (cx > cy)
                return LuaObject.FALSE;
        }
        return LuaObject.of(sx.length() <= sy.length());
    }

    @SuppressWarnings("unchecked")
    public boolean hasKey(LuaObject key) {
        if (!isTable() && !isUserData())
            throw new InternalLuaRuntimeError("This is not a table nor userdata!");

        if (isTable()) {
            return ((LuaHashMap) refVal).containsKey(key);
        } else {
            throw new UnsupportedOperationException("userdata get is not yet supported");
        }
    }

    @SuppressWarnings("unchecked")
    public LuaObject get(LuaObject key) {
        if (!isTable() && !isUserData())
            throw new InternalLuaRuntimeError("This is not a table nor userdata!");

        if (isTable()) {
            return ((LuaHashMap) refVal).getOrDefault(key, NIL);
        } else {
            throw new UnsupportedOperationException("userdata get is not yet supported");
        }
    }

    private boolean impl(boolean a, boolean b) {
        return !a || b;
    }

    @SuppressWarnings("unchecked")
    public void set(LuaObject key, LuaObject value) {
        assert !key.isNil();
        assert impl(key.isDouble(), !key.isNaN());
        if (!isTable() && !isUserData())
            throw new InternalLuaRuntimeError("This is not a table nor userdata!");

        if (isTable()) {
            ((LuaHashMap) refVal).put(key, value);
        } else {
            throw new UnsupportedOperationException("userdata get is not yet supported");
        }
    }

    public void set(String key, LuaObject value) {
        set(LuaObject.of(key), value);
    }

    public LuaObject get(String key) {
        return get(LuaObject.of(key));
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
     * This method is directly taken from {@linkplain Lexer}.
     * TODO: correctly coerce Long.MIN_VAL to long instead of double
     */
    private CoercedString coerceToNumber() {
        assert isString() : "trying to coerce non-string";
        if ((markWord & 0b11) != 0) {
            // already coerced this string, only read cached result
            return new CoercedString((markWord & 1) != 0, dVal, lVal);
        } else if ((markWord & 0b100) != 0) {
            // already failed coercing this string
            return null;
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
            markWord |= 0b100;
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
                    markWord |= 0b100;
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
            // failed to coerce
            markWord |= 0b100;
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
        }
        catch (NumberFormatException e) {
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

    public double asDouble() {
        if (isString()) {
            var cres = coerceToNumber();
            if (cres == null) {
                throw new InternalLuaRuntimeError("coersion of uncoercable string '%s'".formatted(getString()));
            }
            return cres.isDouble ? cres.dVal : (double) cres.lVal;
        } else if (isLong()) {
            return (double) lVal;
        } else if (isDouble()) {
            return dVal;
        } else {
            throw new InternalLuaRuntimeError("coersion of uncoercable object of type '%s'".formatted(getType()));
        }
    }

    public long asLong() {
        if (isString()) {
            var cres = coerceToNumber();
            if (cres != null) {
                if (cres.isDouble) {
                    long lv = (long) cres.dVal;
                    if ((double) lv == cres.dVal) {
                        return lv;
                    }
                } else {
                    return cres.lVal;
                }
            }
            // throws is number is a double and is also not castable to long
            throw new InternalLuaRuntimeError("long-coersion of uncoercable string '%s'".formatted(getString()));
        } else if (isLong()) {
            return lVal;
        } else if (isDouble() && (double) ((long) dVal) == dVal) {
            return (long) dVal;
        } else {
            throw new InternalLuaRuntimeError("long-coersion of uncoercable object of type '%s'".formatted(getType()));
        }
    }

    public String asString() {
        return switch (type) {
            case Types.NIL -> "nil";
            case Types.BOOLEAN -> this == TRUE ? "true" : "false";
            case Types.DOUBLE -> doubleToStringFormat.format(dVal);
            case Types.LONG -> longToStringFormat.format(lVal);
            case Types.STRING -> (String) refVal;
            case Types.FUNCTION -> "function: 0x" + Integer.toHexString(System.identityHashCode(this));
            case Types.USERDATA -> "userdata: 0x" + Integer.toHexString(System.identityHashCode(this));
            case Types.THREAD -> "thread: 0x" + Integer.toHexString(System.identityHashCode(this));
            case Types.TABLE -> "table: 0x" + Integer.toHexString(System.identityHashCode(this));
            case Types.ARRAY -> "array: 0x" + Arrays.toString(asArray());
            case Types.BOX -> "box of <" + ((LuaObject) refVal).asString() + ">";
            default -> throw new IllegalStateException("Unexpected case value: " + type);
        };
    }

    public LuaHashMap asMap() {
        if (!isTable()) {
            throw new InternalLuaRuntimeError("This is not a table!");
        }
        return (LuaHashMap) refVal;
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

    public boolean isIntCoercible() { // TODO rename to isLongCoercible
        return isLong() || (isDouble() && (double) ((long) dVal) == dVal);
    }

    public boolean hasLongRepr() {
        if (isIntCoercible()) {
            return true;
        } else if (isString()) {
            var cres = coerceToNumber();
            return cres != null && (!cres.isDouble || (double) ((long) cres.dVal) == cres.dVal);
        } else {
            return false;
        }
    }

    public boolean isNumberCoercible() {
        if (isNumber()) {
            return true;
        } else return isString() && coerceToNumber() != null;
    }

    // =================================================================================================================
    // construction methods
    // =================================================================================================================

    public static LuaObject box(LuaObject obj) {
        return new LuaObject(obj, 0, 0, Types.BOX);
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

    public static LuaObject ofB64(String val) {
        return new LuaObject(new String(Base64.getDecoder().decode(val), StandardCharsets.UTF_8), 0, 0, Types.STRING);
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
        assert (val.length & 1) == 0;
        var rv = new LuaHashMap();
        for (int i = 0; i < val.length; i += 2) {
            rv.put(val[i], val[i + 1]);
            assert !val[i].isDouble() || val[i].isDouble() && !val[i].isIntCoercible();
        }
        return new LuaObject(rv, -1, -1, Types.TABLE);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof LuaObject other) {
            return switch (type) {
                case Types.NIL -> other.isNil();
                case Types.BOOLEAN -> other.isBoolean() && (getBool() == other.getBool());
                case Types.DOUBLE -> other.isDouble() && (dVal == other.dVal);
                case Types.LONG -> other.isLong() && (lVal == other.lVal);
                case Types.STRING -> other.isString() && (refVal.equals(other.refVal));
                case Types.FUNCTION, Types.USERDATA, Types.THREAD, Types.TABLE, Types.ARRAY, Types.BOX -> this == other;
                default -> throw new IllegalStateException("Unexpected case value: " + type);
            };
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return switch (type) {
            case Types.NIL -> 0;
            case Types.BOOLEAN -> this == TRUE ? 1 : 2;
            case Types.DOUBLE ->
                    type * 31 + ((int) (Double.doubleToRawLongBits(dVal) >> 32)) * 31 + ((int) Double.doubleToRawLongBits(dVal));
            case Types.LONG -> type * 31 + ((int) (lVal >> 32)) * 31 + ((int) lVal);
            case Types.STRING, Types.FUNCTION, Types.USERDATA, Types.THREAD, Types.TABLE, Types.ARRAY ->
                    type * 31 + refVal.hashCode();
            case Types.BOX -> type * 31 + System.identityHashCode(this);
            default -> throw new IllegalStateException("Unexpected case value: " + type);
        };
    }

    @Override
    public String toString() {
        return switch (type) {
            case Types.NIL,Types.BOOLEAN, Types.DOUBLE, Types.LONG, Types.STRING -> "%s: %s".formatted(getTypeAsString(), asString());
            default -> asString();
        };
    }
}
