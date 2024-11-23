package dev.asdf00.jluavm.runtime.types;

import dev.asdf00.jluavm.utils.Tuple;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

public class LuaHashMap {
    private final TreeSet<Long> upperSequenceBounds = new TreeSet<>();
    private final TreeSet<Long> lowerSequenceBounds = new TreeSet<>();
    private KeyNode head = null;
    private KeyNode tail = null;
    private final Map<LuaObject, Tuple<LuaObject, KeyNode>> backing = new HashMap<>();

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

    public void put(LuaObject key, LuaObject value) {
        boolean alreadyExists = backing.containsKey(key);
        if (!alreadyExists) {
            if (tail == null) {
                // init entry list
                head = new KeyNode(key);
                tail = head;
            } else {
                // append
                tail.after = new KeyNode(key, tail);
                tail = tail.after;
            }
        }
        if (key.isLong() && key.asLong() > 0) {
            var prev = alreadyExists ? backing.get(key).x() : null;
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
        backing.put(key, new Tuple<>(value, tail));
    }

    public LuaObject remove(LuaObject key) {
        if (backing.containsKey(key)) {
            var entry = backing.get(key);
            if (backing.size() > 1) {
                // still retains at least 1 entry after removal
                var eNode = entry.y();
                var b = eNode.before;
                var a = eNode.after;
                if (b == null) {
                    // first entry
                    head = a;
                    a.before = null;
                } else if (a == null) {
                    // last entry
                    tail = b;
                    b.after = null;
                } else {
                    // middle entry
                    b.after = a;
                    a.before = b;
                }
            } else {
                // map is empty now
                head = null;
                tail = null;
            }
            if (key.isLong() && key.asLong() > 0) {
                var prev = entry.x();
                if (prev != null && !prev.isNil()) {
                    createHole(key.asLong());
                }
            }
            return entry.x();
        }
        return null;
    }

    public boolean remove(LuaObject key, LuaObject value) {
        if (backing.containsKey(key)) {
            var val = backing.get(key);
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

    public LuaObject getOrDefault(Object key, LuaObject defaultValue) {
        return backing.containsKey(key) ? backing.get(key).x() : defaultValue;
    }

    public boolean containsKey(LuaObject key) {
        return backing.containsKey(key);
    }

    public void clear() {
        lowerSequenceBounds.clear();
        upperSequenceBounds.clear();
        backing.clear();
        head = null;
        tail = null;
    }

    public LuaObject getKeyAfter(LuaObject key) {
        var entry = backing.get(key);
        if (entry != null) {
            var a = entry.y().after;
            return a == null ? LuaObject.nil() : a.key;
        } else {
            return null;
        }
    }

    public LuaObject getFirstKey() {
        return head == null ? LuaObject.nil() : head.key;
    }

    private static final class KeyNode {
        public final LuaObject key;
        public KeyNode before;
        public KeyNode after;

        public KeyNode(LuaObject key) {
            this(key, null);
        }

        public KeyNode(LuaObject key, KeyNode before) {
            this.key = key;
            this.before = before;
            this.after = null;
        }
    }
}
