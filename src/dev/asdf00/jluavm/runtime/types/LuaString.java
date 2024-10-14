package dev.asdf00.jluavm.runtime.types;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class LuaString implements ILuaVariable {
    public final String value;

    private LuaString(String value) {
        this.value = value;
    }

    public static LuaString of(String value) {
        return new LuaString(value);
    }

    public static LuaString ofB64(String b64Value) {
        return new LuaString(new String(Base64.getDecoder().decode(b64Value), StandardCharsets.UTF_8));
    }
}
