package dev.asdf00.jluavm.types;

public class LuaString$ extends LuaVariable$ {
    private final String content;

    public LuaString$(String content) {
        super(LuaType.STR);
        this.content = content;
    }

    public LuaVariable$ concat(LuaString$ y) {
        return new LuaString$(content + y.content);
    }

    public int getLength() {
        return content.length();
    }
}
