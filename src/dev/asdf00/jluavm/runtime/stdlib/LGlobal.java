package dev.asdf00.jluavm.runtime.stdlib;

import dev.asdf00.jluavm.runtime.errors.LuaUserError;
import dev.asdf00.jluavm.runtime.types.AtomicLuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;

public class LGlobal {

    public static LuaObject getTable(){
        var rv = LuaObject.table();
        /* TODO, add the following ones: https://www.lua.org/manual/5.4/manual.html#6.1
        collectgarbage
        dofile
        error
        getmetatable
        ipairs
        load
        loadfile
        next
        pairs
        pcall
        print
        rawequal
        rawget
        rawlen
        rawset
        require
        select
        setmetatable
        tonumber
        warn
        xpcall
         */
        rv.set("assert", LuaObject.of(AtomicLuaFunction.forOneResult((vm, x, msg) -> {
         if(x.isTruthy())
             return x;

         vm.error(new LuaUserError(msg.isNil() ? "assertion failed!" : msg.asString()));
         return null;
        })));
        rv.set("tostring", LuaObject.of(AtomicLuaFunction.forOneResult((vm, x) -> LuaObject.of(x.asString())))); // TODO call metatable __tostring if exists and return that instead
        rv.set("type", LuaObject.of(AtomicLuaFunction.forOneResult((vm, x) -> LuaObject.of(x.getTypeAsString()))));

        rv.set("_VERSION", LuaObject.of("Lua 5.4"));
        return rv;
    }
}
