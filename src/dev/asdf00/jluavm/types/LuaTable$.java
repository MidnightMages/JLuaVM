package dev.asdf00.jluavm.types;

public class LuaTable$ extends LuaVariable$ {
    public LuaTable$() {
        super(LuaType.TABLE);
    }

    private LuaTable$ metatable = null;

    public LuaFunction$ getMtFunc(String funcName){ // TODO for __index this may be a table too --> maybe for other funcs thats allowed too?; see https://www.lua.org/manual/5.4/manual.html#2.4
        if(metatable != null){
            var mv = metatable.get(new LuaString$(funcName));
            if (mv.isFunction())
                return ((LuaFunction$) mv);
        }
        return null;
    }

    public LuaVariable$ get(LuaVariable$ key){
        return null;
    }

    public LuaVariable$ getLength() {
        throw new UnsupportedOperationException("not implemented");
    }
}
