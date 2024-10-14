package dev.asdf00.jluavm.runtime.types;

public class LuaNil implements ILuaVariable {
    @Override
    public boolean isNil() {
        return true;
    }
}
