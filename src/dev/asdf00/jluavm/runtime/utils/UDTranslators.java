package dev.asdf00.jluavm.runtime.utils;

import dev.asdf00.jluavm.api.userdata.LuaUserData;
import dev.asdf00.jluavm.exceptions.LuaConversionException;
import dev.asdf00.jluavm.runtime.types.LuaObject;

import java.util.Arrays;

public class UDTranslators {
    public static byte lo2b(LuaObject o) {
        if (!o.isNumber()) {
            throw new LuaConversionException(o + " is not a number");
        }
        if (o.isLong()) {
            long num = o.asLong();
            if (num > Byte.MAX_VALUE) {
                return Byte.MAX_VALUE;
            } else if (num < Byte.MIN_VALUE) {
                return Byte.MIN_VALUE;
            } else {
                return (byte) num;
            }
        } else {
            double num = o.asDouble();
            if (num > Byte.MAX_VALUE) {
                return Byte.MAX_VALUE;
            } else if (num < Byte.MIN_VALUE) {
                return Byte.MIN_VALUE;
            } else {
                return (byte) num;
            }
        }
    }

    public static short lo2s(LuaObject o) {
        if (!o.isNumber()) {
            throw new LuaConversionException(o + " is not a number");
        }
        if (o.isLong()) {
            long num = o.asLong();
            if (num > Short.MAX_VALUE) {
                return Short.MAX_VALUE;
            } else if (num < Short.MIN_VALUE) {
                return Short.MIN_VALUE;
            } else {
                return (short) num;
            }
        } else {
            double num = o.asDouble();
            if (num > Short.MAX_VALUE) {
                return Short.MAX_VALUE;
            } else if (num < Short.MIN_VALUE) {
                return Short.MIN_VALUE;
            } else {
                return (short) num;
            }
        }
    }

    public static int lo2i(LuaObject o) {
        if (!o.isNumber()) {
            throw new LuaConversionException(o + " is not a number");
        }
        if (o.isLong()) {
            long num = o.asLong();
            if (num > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            } else if (num < Integer.MIN_VALUE) {
                return Integer.MIN_VALUE;
            } else {
                return (int) num;
            }
        } else {
            double num = o.asDouble();
            if (num > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            } else if (num < Integer.MIN_VALUE) {
                return Integer.MIN_VALUE;
            } else {
                return (int) num;
            }
        }
    }

    public static long lo2l(LuaObject o) {
        if (!o.isNumber()) {
            throw new LuaConversionException(o + " is not a number");
        }
        if (o.isLong()) {
            return o.asLong();
        } else {
            double num = o.asDouble();
            if (num > Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            } else if (num < Long.MIN_VALUE) {
                return Long.MIN_VALUE;
            } else {
                return (long) num;
            }
        }
    }

    public static float lo2f(LuaObject o) {
        if (!o.isNumber()) {
            throw new LuaConversionException(o + " is not a number");
        }
        return o.isLong() ? o.asLong() : (float) o.asDouble();
    }

    public static double lo2d(LuaObject o) {
        if (!o.isNumber()) {
            throw new LuaConversionException(o + " is not a number");
        }
        return o.isLong() ? o.asLong() : o.asDouble();
    }

    public static boolean lo2z(LuaObject o) {
        if (!o.isBoolean()) {
            throw new LuaConversionException(o + " is not a boolean");
        }
        return o == LuaObject.TRUE;
    }

    public static char lo2c(LuaObject o) {
        if (!o.isString() || o.asString().length() != 1) {
            throw new LuaConversionException(o + " is not a string with length 1");
        }
        return o.asString().charAt(0);
    }

    public static String lo2st(LuaObject o) {
        if (o.isNil()) {
            return null;
        }
        if (!o.isString()) {
            throw new LuaConversionException(o + " is not a string");
        }
        return o.asString();
    }

    @SuppressWarnings("unchecked")
    public static <T extends LuaUserData> T lo2ud(Class<T> clazz, LuaObject o) {
        if (o.isNil()) {
            return null;
        }
        if (!o.isUserData() || !clazz.isAssignableFrom(o.refVal.getClass())) {
            throw new LuaConversionException(o + " is not a userdata " + clazz.getName());
        }
        return (T) o.refVal;
    }

    public static <T extends Enum<T>> T lo2en(Class<T> clazz, LuaObject o) {
        if (o.isNil()) {
            return null;
        }
        if (!o.isString()) {
            throw new LuaConversionException("'%s' is not a valid %s (available values: %s)".formatted(
                    o.asString(), clazz.getName(), Arrays.toString(clazz.getEnumConstants())));
        }
        try {
            return Enum.valueOf(clazz, o.getString());
        } catch (IllegalArgumentException e) {
            throw new LuaConversionException("'%s' is not a valid %s (available values: %s)".formatted(
                    o.getString(), clazz.getName(), Arrays.toString(clazz.getEnumConstants())));
        }
    }

    private enum MyTest {
        A, B, C
    }

    public static LuaObject lo2null(LuaObject o) {
        if (o.isNil()) {
            return null;
        }
        return o;
    }

    public static LuaObject null2lo(LuaObject o) {
        if (o == null) {
            return LuaObject.nil();
        }
        return o;
    }
}
