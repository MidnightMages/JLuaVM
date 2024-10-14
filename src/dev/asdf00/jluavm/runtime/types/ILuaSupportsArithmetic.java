package dev.asdf00.jluavm.runtime.types;

/**
 * Coerces to number on arithmetic operation.
 */
public interface ILuaSupportsArithmetic extends ILuaVariable {
    ILuaNumber add(ILuaSupportsArithmetic other);
}
