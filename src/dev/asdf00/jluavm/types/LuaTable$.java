package dev.asdf00.jluavm.types;

import dev.asdf00.jluavm.exceptions.runtime.LuaArgumentError$;
import dev.asdf00.jluavm.exceptions.runtime.LuaNilError$;
import dev.asdf00.jluavm.internals.LuaVM_RT$;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public final class LuaTable$ extends LuaVariable$ implements ILuaIndexable$ {
    private LuaTable$ metatable = null;
    private Map<LuaVariable$, LuaVariable$> table;

    protected LuaTable$(Map<LuaVariable$, LuaVariable$> innerTable) {
        super(LuaType.TABLE);
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

    public LuaFunction$ _luaGetMtFunc(LuaVM_RT$ vmHandle, String funcName) { // TODO for __index this may be a table too --> maybe for other funcs thats allowed too?; see https://www.lua.org/manual/5.4/manual.html#2.4
        if(metatable != null){
            var mv = metatable._luaGet(vmHandle, new LuaString$(funcName));
            if (mv.isFunction()) {
                return ((LuaFunction$) mv);
            }
        }
        return null;
    }

    @SuppressWarnings("unused")
    public LuaVariable$ rawget(LuaVM_RT$ vmHandle, LuaVariable$ key) {
        key = tryCoerceFloat(key);
        LuaVariable$ result = table.get(key);
        return result == null ? LuaNil$.singleton : result;
    }

    public LuaVariable$ _luaGet(LuaVM_RT$ vmHandle, LuaVariable$ key) {
        // TODO: check for possible meta table entry
        // TODO: coerce LuaNumber to LuaNumberBw for key if possible
        key = tryCoerceFloat(key);
        if (!table.containsKey(key)) {
            var mtf = _luaGetMtFunc(vmHandle, "__index");
            if (mtf != null) {

            }
        }
        return null;
    }

    public LuaTable$ rawset(LuaVM_RT$ vmHandle, LuaVariable$ key, LuaVariable$ value) {
        key = tryCoerceFloat(key);
        if (key.isNil()) {
            vmHandle.yeet(new LuaNilError$("can not set table field 'nil'"));
        } else if (key instanceof LuaNumber$ ln && Double.isNaN(ln.getValue())) {
            vmHandle.yeet(new LuaNilError$("can not set table field 'NaN'"));
        }
        if (value.isNil()) {
            table.remove(key);
        } else {
            table.put(key, value);
        }
        return this;
    }

    public void _luaGet(LuaVM_RT$ vmHandle, LuaVariable$ key, LuaVariable$ value){
        // coerce LuaNumber to LuaNumberBw for key if possible
        key = tryCoerceFloat(key);
        // check for possible meta table entry
        if (!table.containsKey(key)) {
            var mtf = _luaGetMtFunc(vmHandle, "__newindex");
            if (mtf != null) {
                // if there is a meta function, we execute it instead
                mtf.invoke(vmHandle, key, value);
                return;
            }
        }
        rawset(vmHandle, key, value);
    }

    public LuaVariable$ getLength() {
        throw new UnsupportedOperationException("not implemented");
    }

    private static LuaVariable$ tryCoerceFloat(LuaVariable$ key) {
        if (key instanceof LuaNumber$ ln) {
            double dn = ln.getValue();
            if ((double) ((long) dn) == dn) {
                key = LuaNumberBw$.of((long) dn);
            }
        }
        return key;
    }
}
