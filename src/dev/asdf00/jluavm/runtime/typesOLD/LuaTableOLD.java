package dev.asdf00.jluavm.runtime.typesOLD;

import dev.asdf00.jluavm.exceptions.runtime.LuaArgumentError$;
import dev.asdf00.jluavm.exceptions.runtime.LuaNilError$;
import dev.asdf00.jluavm.internals.LuaVM_RT;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public final class LuaTableOLD extends LuaVariableOLD implements ILuaIndexableOLD {
    private LuaTableOLD metatable = null;
    private Map<LuaVariableOLD, LuaVariableOLD> table;

    protected LuaTableOLD(Map<LuaVariableOLD, LuaVariableOLD> innerTable) {
        super(LuaType.TABLE);
        table = innerTable;
    }

    public static LuaTableOLD of(LuaVM_RT vmHandle, LuaVariableOLD... keyVals) {
        if ((keyVals.length & 1) == 1) {
            vmHandle.yeet(new LuaArgumentError$("got more keys than values"));
        }
        var forbiddenIndexes = new HashSet<Double>();
        for (int i = 0; i < keyVals.length - 1; i += 2) {
            if (keyVals[i] != null) {
                if (keyVals[i] instanceof LuaNumberOLD dn) {
                    forbiddenIndexes.add(dn.value);
                } else if (keyVals[i] instanceof LuaNumberBwOLD in) {
                    forbiddenIndexes.add((double) in.value);
                }
            }
        }
        var tbl = new HashMap<LuaVariableOLD, LuaVariableOLD>();
        double lIdx = 1.0;
        for (int i = 0; i < keyVals.length - 1;) {
            var key = keyVals[i++];
            var val = keyVals[i++];
            if (key == null) {
                key = LuaNumberOLD.of(lIdx++);
            }
            tbl.put(key, val);
        }
        return new LuaTableOLD(tbl);
    }

    public LuaFunctionOLD _luaGetMtFunc(LuaVM_RT vmHandle, String funcName) { // TODO for __index this may be a table too --> maybe for other funcs thats allowed too?; see https://www.lua.org/manual/5.4/manual.html#2.4
        if(metatable != null){
            var mv = metatable._luaGet(vmHandle, new LuaStringOLD(funcName));
            if (mv.isFunction()) {
                return ((LuaFunctionOLD) mv);
            }
        }
        return null;
    }

    @SuppressWarnings("unused")
    public LuaVariableOLD rawget(LuaVM_RT vmHandle, LuaVariableOLD key) {
        key = tryCoerceFloat(key);
        LuaVariableOLD result = table.get(key);
        return result == null ? LuaNilOLD.singleton : result;
    }

    public LuaVariableOLD _luaGet(LuaVM_RT vmHandle, LuaVariableOLD key) {
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

    public LuaTableOLD rawset(LuaVM_RT vmHandle, LuaVariableOLD key, LuaVariableOLD value) {
        key = tryCoerceFloat(key);
        if (key.isNil()) {
            vmHandle.yeet(new LuaNilError$("can not set table field 'nil'"));
        } else if (key instanceof LuaNumberOLD ln && Double.isNaN(ln.getValue())) {
            vmHandle.yeet(new LuaNilError$("can not set table field 'NaN'"));
        }
        if (value.isNil()) {
            table.remove(key);
        } else {
            table.put(key, value);
        }
        return this;
    }

    public void _luaGet(LuaVM_RT vmHandle, LuaVariableOLD key, LuaVariableOLD value){
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

    public LuaVariableOLD getLength() {
        throw new UnsupportedOperationException("not implemented");
    }

    private static LuaVariableOLD tryCoerceFloat(LuaVariableOLD key) {
        if (key instanceof LuaNumberOLD ln) {
            double dn = ln.getValue();
            if ((double) ((long) dn) == dn) {
                key = LuaNumberBwOLD.of((long) dn);
            }
        }
        return key;
    }
}
