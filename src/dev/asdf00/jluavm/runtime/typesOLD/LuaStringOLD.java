package dev.asdf00.jluavm.runtime.typesOLD;

public class LuaStringOLD extends LuaVariableOLD {
    private final String content;

    public LuaStringOLD(String content) {
        super(LuaType.STR);
        this.content = content;
    }

    public static LuaStringOLD of(String content) {
        return new LuaStringOLD(content);
    }

    public LuaVariableOLD concat(LuaStringOLD y) {
        return new LuaStringOLD(content + y.content);
    }

    public int getLength() {
        return content.length();
    }

    public boolean strEquals(LuaStringOLD y) {
        return content.equals(y.content);
    }

    public boolean lt(LuaStringOLD y) {
        throw new UnsupportedOperationException("not implemented");
    }

    public boolean le(LuaStringOLD y) {
        throw new UnsupportedOperationException("not implemented");
    }
}
