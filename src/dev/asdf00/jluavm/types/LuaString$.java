package dev.asdf00.jluavm.types;

public class LuaString$ extends LuaVariable$ {
    private final String content;

    public LuaString$(String content) {
        super(LuaType.STR);
        this.content = content;
    }

    public static LuaString$ of(String content) {
        return new LuaString$(content);
    }

    public LuaVariable$ concat(LuaString$ y) {
        return new LuaString$(content + y.content);
    }

    public int getLength() {
        return content.length();
    }

    public boolean strEquals(LuaString$ y) {
        return content.equals(y.content);
    }

    public boolean lt(LuaString$ y) {
        throw new UnsupportedOperationException("not implemented");
    }

    public boolean le(LuaString$ y) {
        throw new UnsupportedOperationException("not implemented");
    }
}
