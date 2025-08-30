package dev.asdf00.jluavm.runtime.types;

import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;
import dev.asdf00.jluavm.exceptions.loading.LuaParserException;
import dev.asdf00.jluavm.internals.Coroutine;
import dev.asdf00.jluavm.parsing.Lexer;
import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.utils.ByteArrayBuilder;

import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

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
        public static final int EXTENDABLE = NIL | BOOLEAN | NUMBER | STRING | FUNCTION | THREAD | TABLE;
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

    public LuaObject getMetaValueOrNil(String mtKey) {
        return getMetaValueOrNil(LuaObject.of(mtKey))
                ;
    }

    public LuaObject getMetaValueOrNil(LuaObject mtKey) {
        return metaTable != null ? metaTable.get(mtKey) : LuaObject.nil();
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
            case Types.TABLE -> "table";
            case Types.ARRAY -> "_array";
            case Types.BOX -> "_box<" + ((LuaObject) refVal).getTypeAsString() + ">";
            default -> throw new IllegalStateException("Unexpected case value: " + type);
        };
    }

    public boolean isNaN() {
        return isDouble() && Double.isNaN(dVal);
    }

    public boolean isTruthy() {
        return !isNil() && this != FALSE;
    }

    public boolean isExtendable() {
        return isType(Types.EXTENDABLE);
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

        if (doubleCalc) {
            return LuaObject.of(Math.floor(dx / dy));
        }
        return LuaObject.of((long) Math.floor((double) lx / ly));
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
        if (doubleCalc) {
            return LuaObject.of(dx - Math.floor(dx / dy) * dy);
        }
        return LuaObject.of(lx - (long) Math.floor((double) lx / ly) * ly);
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
            dx = formatDouble(dVal);
        } else {
            assert isLong();
            dx = longToStringFormat.format(lVal);
        }
        String dy;
        if (other.isString()) {
            dy = other.getString();
        } else if (other.isDouble()) {
            dy = formatDouble(other.dVal);
        } else {
            assert other.isLong();
            dy = longToStringFormat.format(other.lVal);
        }
        return LuaObject.of(dx + dy);
    }

    private static String formatDouble(double dVal) {
        if (dVal == Double.POSITIVE_INFINITY)
            return "inf";
        if (dVal == Double.NEGATIVE_INFINITY)
            return "-inf";
        if (Double.isNaN(dVal))
            return "-nan";

        var dbl = doubleToStringFormat.format(Math.abs(dVal));
        if (dbl.startsWith("0.0000")) {
            dbl = dbl.substring(2);
            int negExponent = 1;
            for (int i = 0; i < dbl.length(); i++) {
                if (dbl.charAt(i) == '0')
                    negExponent++;
                else
                    break;
            }
            dbl = dbl.substring(negExponent - 1) + "e-%02d".formatted(negExponent); // minimum 2 exp digits, i.e e-05
        }
        return dVal < 0 ? ("-" + dbl) : dbl;
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
        var shl = ly >= 0;
        var lyAbs = Math.abs(ly);
        if (lyAbs >= 64) {
            return LuaObject.of(0);
        }

        return LuaObject.of(shl ? lx << lyAbs : lx >>> lyAbs);
    }

    public LuaObject shr(LuaObject other) {
        assert isIntCoercible();
        assert other.isIntCoercible();

        var lx = isLong() ? lVal : (long) dVal;
        var ly = other.isLong() ? other.lVal : (long) other.dVal;
        var shr = ly >= 0;
        var lyAbs = Math.abs(ly);
        if (lyAbs >= 64) {
            return LuaObject.of(0);
        }

        return LuaObject.of(shr ? lx >>> lyAbs : lx << lyAbs);
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
                return LuaObject.of(lVal <= other.lVal);

            var dx = isLong() ? (double) lVal : dVal;
            var dy = other.isLong() ? (double) other.lVal : other.dVal;
            return LuaObject.of(dx <= dy);
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

    public boolean hasKey(LuaObject key) {
        if (!isTable() && !isUserData())
            throw new InternalLuaRuntimeError("This is not a table nor userdata!");

        if (isTable()) {
            return ((LuaHashMap) refVal).containsKey(key);
        } else {
            throw new UnsupportedOperationException("userdata get is not yet supported");
        }
    }

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
            throw new InternalLuaRuntimeError("This is not a box, but is of type %s!".formatted(getTypeAsString()));
        }
        return (LuaObject) refVal;
    }

    // =================================================================================================================
    // coerse to number
    // =================================================================================================================

    private static final String STRING_LONG_MIN_VALUE = Long.toString(Long.MIN_VALUE);

    public CoercedString coerceToNumber() {
        assert isString() : "trying to coerce non-string";
        if ((markWord & 0b11) != 0) {
            // already coerced this string, only read cached result
            return new CoercedString((markWord & 1) != 0, dVal, lVal);
        } else if ((markWord & 0b100) != 0) {
            // already failed coercing this string
            return null;
        }

        // actual parsing
        try {
            var charsRaw = this.asString().strip();
            if (charsRaw.equals(STRING_LONG_MIN_VALUE)) {
                markWord |= 0b10;
                dVal = -1;
                lVal = Long.MIN_VALUE;
                return new CoercedString(false, -1, Long.MIN_VALUE);
            }
            int mul = 1;
            int[] currCharPtr = new int[]{0};
            if (charsRaw.charAt(0) == '-') {
                mul = -1;
                charsRaw = charsRaw.substring(1);
            } else if (charsRaw.charAt(0) == '+') {
                charsRaw = charsRaw.substring(1);
            }
            var chars = charsRaw;
            var res = Lexer.parseNumber(new Position(0, 0, 0),
                    () -> currCharPtr[0] >= chars.length() ? (char) -1 : chars.charAt(currCharPtr[0]),
                    () -> currCharPtr[0]++);
            if (res.consumedString().equals(chars)) {
                // save result to cache
                var isInteger = res.dVal() < 0;
                markWord |= 1 << (isInteger ? 1 : 0);
                if (isInteger) {
                    markWord |= 0b10;
                    lVal = res.lVal() * mul;
                    dVal = -1;
                } else {
                    markWord |= 1;
                    dVal = res.dVal() * mul;
                    lVal = -1;
                }
                return new CoercedString(!isInteger, dVal, lVal);
            }
            // else fallthrough
        } catch (LuaParserException ignored) {
        }
        // failed to coerce
        markWord |= 0b100;
        return null;
    }

    public record CoercedString(boolean isDouble, double dVal, long lVal) {
    }

    // =================================================================================================================
    // getter methods
    // =================================================================================================================

    public Coroutine asCoroutine() {
        if (!isThread()) {
            throw new InternalLuaRuntimeError("This is not a thread!");
        }
        return (Coroutine) refVal;
    }

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
            case Types.DOUBLE -> formatDouble(dVal);
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

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
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

    @SuppressWarnings("SameReturnValue")
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
        if (val == null) {
            return new LuaObject(null, 0, 0, Types.FUNCTION);
        }
        if (val.selfLuaObj == null) {
            var nu = new LuaObject(val, 0, 0, Types.FUNCTION);
            val.selfLuaObj = nu;
            return nu;
        } else {
            return val.selfLuaObj;
        }
    }

    public static LuaObject of(ILuaUserData val) {
        return new LuaObject(val, 0, 0, Types.USERDATA);
    }

    public static LuaObject of(LuaObject... val) {
        return new LuaObject(val, 0, 0, Types.ARRAY);
    }

    public static LuaObject of(Coroutine coroutine) {
        return new LuaObject(coroutine, 0, 0, Types.THREAD);
    }

    public static LuaObject table(LuaObject... val) {
        assert (val.length & 1) == 0;
        if (val.length == 0) {
            return new LuaObject(new LuaHashMap(), -1, -1, Types.TABLE);
        }
        var rv = new LuaHashMap();
        var noIdxFields = new ArrayList<LuaObject>(val.length / 2);
        boolean lastIsNoIdx = false;
        for (int i = 0; i < val.length; i += 2) {
            if (val[i] == null) {
                // delay insertion
                noIdxFields.add(val[i + 1]);
                if (i == val.length - 2) {
                    lastIsNoIdx = true;
                }
                continue;
            }
            LuaObject insertion = val[i + 1];
            if (insertion.isArray()) {
                LuaObject[] arr = insertion.asArray();
                insertion = arr.length > 0 ? arr[0] : LuaObject.nil();
            }
            rv.put(val[i], insertion);
            assert !val[i].isDouble() || val[i].isDouble() && !val[i].isIntCoercible();
        }
        for (int i = 0; i < noIdxFields.size(); i++) {
            var cur = noIdxFields.get(i);
            if (cur.isArray()) {
                LuaObject[] arr = cur.asArray();
                if (lastIsNoIdx && i == noIdxFields.size() - 1) {
                    // insert entire array if last element in constructor
                    for (int j = 0; j < arr.length; j++) {
                        rv.put(LuaObject.of(i + 1 + j), arr[j]);
                    }
                } else {
                    // only take first element
                    rv.put(LuaObject.of(i + 1), arr.length > 0 ? arr[0] : LuaObject.nil());
                }
            } else {
                rv.put(LuaObject.of(i + 1), cur);
            }
        }
        return new LuaObject(rv, -1, -1, Types.TABLE);
    }

    public static LuaObject tableFromArray(LuaObject... arr) {
        var tbl = LuaObject.table();
        for (int i = 0; i < arr.length; i++) {
            tbl.set(LuaObject.of(i + 1), arr[i]);
        }
        return tbl;
    }

    public static LuaObject wrapMap(LuaHashMap map) {
        return new LuaObject(map, -1, -1, Types.TABLE);
    }

    // =================================================================================================================
    // system helpers
    // =================================================================================================================

    /**
     * Serializes this object into serialData and adds a mapping into mappedObjs and returns its own index in serialData.
     */
    public int serialize(List<byte[]> serialData, Map<LuaObject, Integer> mappedObjs) {
        if (mappedObjs.containsKey(this)) {
            return mappedObjs.get(this);
        }
        int ownIdx = serialData.size();
        mappedObjs.put(this, ownIdx);
        switch (type) {
            case Types.NIL -> {
                serialData.add(new byte[]{Types.NIL, 0, 0, 0});
            }
            case Types.BOOLEAN -> {
                serialData.add(new byte[]{Types.BOOLEAN, 0, 0, 0, (byte) lVal});
            }
            case Types.DOUBLE -> {
                serialData.add(new ByteArrayBuilder(12)
                        .append(Types.DOUBLE)
                        .append(Double.doubleToRawLongBits(dVal))
                        .toArray());
            }
            case Types.LONG -> {
                serialData.add(new ByteArrayBuilder(12)
                        .append(Types.LONG)
                        .append(lVal)
                        .toArray());
            }
            case Types.STRING -> {
                serialData.add(new ByteArrayBuilder()
                        .append(Types.STRING)
                        .appendAll(asString().getBytes(StandardCharsets.UTF_8))
                        .toArray());
            }
            case Types.FUNCTION -> {
                // reserve space in serialData
                serialData.add(null);
                var bb = new ByteArrayBuilder().append(Types.FUNCTION);
                getFunc().serialize(serialData, mappedObjs, bb);
                serialData.set(ownIdx, bb.toArray());
            }
            case Types.USERDATA -> {
                throw new UnsupportedOperationException("serializing userdata is not implemented");
            }
            case Types.THREAD -> {
                // reserve space in serialData
                serialData.add(null);
                var bb = new ByteArrayBuilder().append(Types.THREAD);
                asCoroutine().serialize(serialData, mappedObjs, bb);
                serialData.set(ownIdx, bb.toArray());
            }
            case Types.TABLE -> {
                // reserve space in serialData
                serialData.add(null);
                var bb = new ByteArrayBuilder();
                bb.append(Types.TABLE);
                bb.append(metaTable == null
                        ? -1 // invalid obj index to indicate null
                        : metaTable.serialize(serialData, mappedObjs));
                var map = asMap();
                for (var k : map.keys()) {
                    assert !k.isArray();
                    bb.append(k.serialize(serialData, mappedObjs));
                    LuaObject v = map.getOrDefault(k, NIL);
                    assert !v.isArray() && !v.isNil();
                    bb.append(v.serialize(serialData, mappedObjs));
                }
                // fixup in serialData
                serialData.set(ownIdx, bb.toArray());
            }
            case Types.ARRAY -> {
                // reserve space in serialData
                serialData.add(null);
                var bb = new ByteArrayBuilder();
                bb.append(Types.ARRAY);
                for (var v : asArray()) {
                    bb.append(v == null ? -1 : v.serialize(serialData, mappedObjs));
                }
                serialData.set(ownIdx, bb.toArray());
            }
            case Types.BOX -> {
                // reserve space in serialData
                serialData.add(null);
                int containing = unbox().serialize(serialData, mappedObjs);
                serialData.set(ownIdx, new ByteArrayBuilder(8)
                        .append(Types.BOX)
                        .append(containing)
                        .toArray());
            }
            default -> throw new IllegalStateException("Unexpected case value: " + type);
        }
        return ownIdx;
    }

    @Override
    public int hashCode() {
        return switch (type) {
            case Types.NIL -> 0;
            case Types.BOOLEAN -> this == TRUE ? 1 : 2;
            case Types.DOUBLE ->
                    type * 31 + ((int) (Double.doubleToRawLongBits(dVal) >> 32)) * 31 + ((int) Double.doubleToRawLongBits(dVal));
            case Types.LONG -> type * 31 + ((int) (lVal >> 32)) * 31 + ((int) lVal);
            case Types.STRING -> type * 31 + refVal.hashCode();
            case Types.FUNCTION, Types.USERDATA, Types.THREAD, Types.TABLE, Types.ARRAY, Types.BOX ->
                    type * 31 + System.identityHashCode(this);
            default -> throw new IllegalStateException("Unexpected case value: " + type);
        };
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
    public String toString() {
        return switch (type) {
            case Types.NIL, Types.BOOLEAN, Types.DOUBLE, Types.LONG, Types.STRING ->
                    "%s: %s".formatted(getTypeAsString(), asString());
            default -> asString();
        };
    }
}
