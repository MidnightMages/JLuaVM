package dev.asdf00.jluavm.runtime.types;

import dev.asdf00.jluavm.utils.Tuple;

import java.util.*;

public class LuaHashMap {
    private final TreeSet<Long> upperSequenceBounds = new TreeSet<>();
    private final TreeSet<Long> lowerSequenceBounds = new TreeSet<>();
    private KeyNode head = null;
    private KeyNode tail = null;
    private final Map<LuaObject, Tuple<LuaObject, KeyNode>> backing = new HashMap<>();

    @SuppressWarnings("DataFlowIssue")
    public long luaLen() {
        return upperSequenceBounds.isEmpty() ? 0 : upperSequenceBounds.floor(Long.MAX_VALUE);
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
        if (oFloor == null) {
            if (oCeil == null) {
                // not inside a hole
                lowerSequenceBounds.add(pos);
                upperSequenceBounds.add(pos);
            } else {
                // below lowest hole
                if (oCeil > pos + 1) {
                    // not touching
                    lowerSequenceBounds.add(pos);
                    upperSequenceBounds.add(pos);
                } else {
                    // touching upper sequence, expand sequence
                    lowerSequenceBounds.remove(oCeil);
                    lowerSequenceBounds.add(pos);
                }
            }
        } else {
            if (oCeil == null) {
                // above highest hole
                if (oFloor < pos - 1) {
                    // not touching
                    lowerSequenceBounds.add(pos);
                    upperSequenceBounds.add(pos);
                } else {
                    // touching lower sequence, expand sequence
                    upperSequenceBounds.remove(oFloor);
                    upperSequenceBounds.add(pos);
                }
            } else {
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
        }
    }

    public LuaObject put(LuaObject key, LuaObject value) {
        assert !key.isNil() && !(key.isDouble() && Double.isNaN(key.asDouble()));
        if (value.isNil()) {
            return remove(key);
        }
        boolean alreadyExists = backing.containsKey(key);
        KeyNode assocKey;
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
            if (key.isLong() && key.asLong() > 0) {
                plugHole(key.asLong());
            }
            assocKey = tail;
        } else {
            assocKey = backing.get(key).y();
        }
        var tpl = backing.put(key, new Tuple<>(value, assocKey));
        if(tpl == null)
            return LuaObject.NIL;

        return tpl.x();
    }

    @SuppressWarnings("UnusedReturnValue")
    public LuaObject remove(LuaObject key) {
        if (backing.containsKey(key)) {
            var entry = backing.remove(key);
            if (backing.size() >= 1) {
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

    public LuaObject getOrDefault(LuaObject key, LuaObject defaultValue) {
        return backing.containsKey(key) ? backing.get(key).x() : defaultValue;
    }

    public boolean containsKey(LuaObject key) {
        return backing.containsKey(key);
    }

    @SuppressWarnings("unused")
    public void clear() {
        lowerSequenceBounds.clear();
        upperSequenceBounds.clear();
        backing.clear();
        head = null;
        tail = null;
    }

    public LuaHashMap clone() {
        var nu = new LuaHashMap();
        KeyNode curNode = head;
        while (curNode != null) {
            nu.put(curNode.key, backing.get(curNode.key).x());
            curNode = curNode.after;
        }
        return nu;
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

    public ArrayList<LuaObject> keys() {
        var ks = new ArrayList<LuaObject>();
        var cur = head;
        while (cur != null) {
            ks.add(cur.key);
            cur = cur.after;
        }
        return ks;
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
