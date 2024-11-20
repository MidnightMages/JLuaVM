package dev.asdf00.jluavm.runtime.types;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;

public class LuaHashMap extends HashMap<LuaObject, LuaObject> {
    private final TreeSet<Long> upperSequenceBounds = new TreeSet<>();
    private final TreeSet<Long> lowerSequenceBounds = new TreeSet<>();

    public long luaLen() {
        return upperSequenceBounds.isEmpty() ? 0 : upperSequenceBounds.ceiling(Long.MIN_VALUE);
    }

    private void createHole(long pos) {
        Long oCeil = upperSequenceBounds.ceiling(pos);
        Long oFloor = lowerSequenceBounds.floor(pos);
        if (oCeil == null || oFloor == null) {
            // not inside sequence
            return;
        }
        // inside sequence [floor, ceil], split/shrink/remove sequence
        long ceil = oCeil;
        long floor = oFloor;
        if (floor < pos) {
            // not at lower bound
            if (ceil > pos) {
                // split sequence
                upperSequenceBounds.add(pos - 1);
                lowerSequenceBounds.add(pos + 1);
            } else {
                // at upper bound but not at lower bound, shrink sequence
                upperSequenceBounds.remove(pos);
                upperSequenceBounds.add(pos - 1);
            }
        } else {
            // at lower bound
            if (ceil > pos) {
                // at lower bound but not at upper bound, shrink sequence
                lowerSequenceBounds.remove(pos);
                lowerSequenceBounds.add(pos + 1);
            } else {
                // at 1 element sequence, remove sequence
                lowerSequenceBounds.remove(pos);
                upperSequenceBounds.remove(pos);
            }
        }
    }

    private void plugHole(long pos) {
        Long oCeil = lowerSequenceBounds.higher(pos);
        Long oFloor = upperSequenceBounds.lower(pos);
        if (oCeil == null || oFloor == null) {
            // not inside a hole
            return;
        }
        // inside hole (floor, ceil), split/shrink/plug hole
        long ceil = oCeil;
        long floor = oFloor;
        if (floor + 1 < pos) {
            // not touching lower sequence
            if (ceil - 1 > pos) {
                // not touching any sequence, create new single element sequence
                lowerSequenceBounds.add(pos);
                upperSequenceBounds.add(pos);
            } else {
                // touching only upper sequence, expanding upper sequence
                lowerSequenceBounds.remove(pos + 1);
                lowerSequenceBounds.add(pos);
            }
        } else {
            // touching lower sequence
            if (ceil - 1 > pos) {
                // touching only lower sequence, expand lower sequence
                upperSequenceBounds.remove(pos - 1);
                upperSequenceBounds.add(pos);
            } else {
                // touching upper and lower sequence, merge them into one
                upperSequenceBounds.remove(pos - 1);
                lowerSequenceBounds.remove(pos + 1);
            }
        }
    }

    @Override
    public LuaObject put(LuaObject key, LuaObject value) {
        if (key.isLong() && key.asLong() > 0) {
            var prev = get(key);
            if (prev == null || prev.isNil()) {
                if (value != null && !value.isNil()) {
                    plugHole(key.asLong());
                }
            } else {
                if (value == null || value.isNil()) {
                    createHole(key.asLong());
                }
            }
        }
        return super.put(key, value);
    }

    @Override
    public LuaObject remove(Object k) {
        if (k instanceof LuaObject key && key.isLong() && key.asLong() > 0) {
            var prev = get(k);
            if (prev != null && !prev.isNil()) {
                createHole(key.asLong());
            }
        }
        return super.remove(k);
    }

    @Override
    public boolean remove(Object key, Object value) {
        if (containsKey(key)) {
            var val = get(key);
            if (val == null) {
                if (value == null) {
                    remove(key);
                    return true;
                } else {
                    return false;
                }
            } else {
                if (val == null) {
                    return false;
                } else if (val.equals(value)) {
                    remove(key);
                    return true;
                } else {
                    return false;
                }
            }
        } else {
            return false;
        }
    }

    @Override
    public LuaObject putIfAbsent(LuaObject key, LuaObject value) {
        if (!containsKey(key)) {
            put(key, value);
            return null;
        } else {
            return get(key);
        }
    }

    @Override
    public void putAll(Map<? extends LuaObject, ? extends LuaObject> m) {
        for (var kv : m.entrySet()) {
            LuaObject key = kv.getKey();
            LuaObject value = kv.getValue();
            if (key.isLong() && key.asLong() > 0) {
                var prev = get(key);
                if (prev == null || prev.isNil()) {
                    if (value != null && !value.isNil()) {
                        plugHole(key.asLong());
                    }
                } else {
                    if (value == null || value.isNil()) {
                        createHole(key.asLong());
                    }
                }
            }
        }
        super.putAll(m);
    }

    @Override
    public LuaObject computeIfAbsent(LuaObject key, Function<? super LuaObject, ? extends LuaObject> mappingFunction) {
        if (!containsKey(key)) {
            LuaObject val = mappingFunction.apply(key);
            put(key, val);
            return val;
        } else {
            return get(key);
        }
    }

    @Override
    public LuaObject computeIfPresent(LuaObject key, BiFunction<? super LuaObject, ? super LuaObject, ? extends LuaObject> remappingFunction) {
        if (containsKey(key) && get(key) != null) {
            LuaObject val = remappingFunction.apply(key, get(key));
            if (val == null) {
                remove(key);
                return null;
            }
            put(key, val);
            return val;
        }
        return null;
    }

    @Override
    public LuaObject compute(LuaObject key, BiFunction<? super LuaObject, ? super LuaObject, ? extends LuaObject> remappingFunction) {
        LuaObject val = remappingFunction.apply(key, get(key));
        if (val != null) {
            put(key, val);
        }
        return val;
    }

    @Override
    public LuaObject getOrDefault(Object key, LuaObject defaultValue) {
        return containsKey(key) ? get(key) : defaultValue;
    }

    @Override
    public void clear() {
        lowerSequenceBounds.clear();
        upperSequenceBounds.clear();
        super.clear();
    }
}
