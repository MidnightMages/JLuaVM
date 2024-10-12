package dev.asdf00.jluavm.types;

import dev.asdf00.jluavm.exceptions.runtime.LuaArgumentError$;
import dev.asdf00.jluavm.internals.LuaVM_RT$;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class LuaTable$ extends LuaVariable$ {
    private LuaTable$ metatable = null;
    private Map<LuaVariable$, LuaVariable$> table;

    public LuaTable$() {
        super(LuaType.TABLE);
    }

    protected LuaTable$(Map<LuaVariable$, LuaVariable$> innerTable) {
        this();
        table = innerTable;
    }

    public static LuaTable$ of(LuaVM_RT$ vmHandle, LuaVariable$... keyVals) {
        if ((keyVals.length & 1) == 1) {
            vmHandle.yeet(new LuaArgumentError$("got more keys than values"));
        }
        var forbiddenIndexes = new HashSet<Double>();
        for (int i = 0; i < keyVals.length - 1; i += 2) {
            if (keyVals[i] != null) {
                if (keyVals[i] instanceof LuaNumber$ dn) {
                    forbiddenIndexes.add(dn.value);
                } else if (keyVals[i] instanceof LuaNumberBw$ in) {
                    forbiddenIndexes.add((double) in.value);
                }
            }
        }
        var tbl = new HashMap<LuaVariable$, LuaVariable$>();
        double lIdx = 1.0;
        for (int i = 0; i < keyVals.length - 1;) {
            var key = keyVals[i++];
            var val = keyVals[i++];
            if (key == null) {
                key = LuaNumber$.of(lIdx++);
            }
            tbl.put(key, val);
        }
        return new LuaTable$(tbl);
    }

    public LuaFunction$ getMtFunc(String funcName){ // TODO for __index this may be a table too --> maybe for other funcs thats allowed too?; see https://www.lua.org/manual/5.4/manual.html#2.4
        if(metatable != null){
            var mv = metatable.get(new LuaString$(funcName));
            if (mv.isFunction())
                return ((LuaFunction$) mv);
        }
        return null;
    }

    public LuaVariable$ get(LuaVariable$ key){
        // TODO: check for possible meta table entry
        // TODO: coerce LuaNumberBw to normal LuaNumber
        throw new UnsupportedOperationException("not implemented");
    }

    public LuaVariable$ set(LuaVariable$ key, LuaVariable$ value){
        // TODO: check for possible meta table entry
        // TODO: coerce LuaNumberBw to normal LuaNumber for key
        throw new UnsupportedOperationException("not implemented");
    }

    public LuaVariable$ getLength() {
        throw new UnsupportedOperationException("not implemented");
    }
}
