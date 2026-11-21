package dev.asdf00.jluavm.parsing.ir;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A straight sequence of statements followed by a single {@link Terminator}.
 * <p>
 * Predecessors are split into forward edges ({@link #predecessors}, coming from blocks above in the layout order) and
 * back edges ({@link #backEdgePredecessors}, coming from blocks below, e.g. loop latches or backward gotos).
 * An edge is a back edge iff its target was already placed when the edge was created, i.e.
 * {@code target.order <= source.order}.
 */
public final class BasicBlock {
    public final int id;
    /**
     * Layout index, -1 while the block has not been placed yet.
     */
    int order = -1;
    public final ArrayList<IRElement> statements = new ArrayList<>();
    private Terminator terminator;
    public final ArrayList<BasicBlock> predecessors = new ArrayList<>();
    public final ArrayList<BasicBlock> backEdgePredecessors = new ArrayList<>();

    BasicBlock(int id) {
        this.id = id;
    }

    public int order() {
        return order;
    }

    public Terminator terminator() {
        return terminator;
    }

    public List<BasicBlock> successors() {
        return terminator == null ? List.of() : terminator.successors();
    }

    public boolean hasBackEdgeTo(BasicBlock succ) {
        assert successors().contains(succ) : "B%d is not a successor of B%d".formatted(succ.id, id);
        return succ.isPlacedAtOrAbove(this);
    }

    /**
     * @return a live, read-only view over {@link #predecessors} followed by {@link #backEdgePredecessors}; copy it
     * before modifying edges of this block while iterating.
     */
    public Collection<BasicBlock> allPredecessors() {
        return new AbstractCollection<>() {
            @Override
            public Iterator<BasicBlock> iterator() {
                return new Iterator<>() {
                    private Iterator<BasicBlock> cur = predecessors.iterator();
                    private boolean inBackEdges = false;

                    @Override
                    public boolean hasNext() {
                        if (!cur.hasNext() && !inBackEdges) {
                            cur = backEdgePredecessors.iterator();
                            inBackEdges = true;
                        }
                        return cur.hasNext();
                    }

                    @Override
                    public BasicBlock next() {
                        if (!hasNext()) {
                            throw new NoSuchElementException();
                        }
                        return cur.next();
                    }
                };
            }

            @Override
            public int size() {
                return predecessors.size() + backEdgePredecessors.size();
            }
        };
    }

    public void setTerminator(Terminator t) {
        assert terminator == null : "B%d is already terminated".formatted(id);
        assert order != -1 : "B%d is terminated before being placed".formatted(id);
        terminator = t;
        for (var succ : t.successors()) {
            link(succ);
        }
    }

    public void clearTerminator() {
        if (terminator == null) {
            return;
        }
        for (var succ : terminator.successors()) {
            unlink(succ);
        }
        terminator = null;
    }

    public void retarget(BasicBlock old, BasicBlock nu) {
        assert terminator != null : "B%d has no terminator".formatted(id);
        int cnt = terminator.replaceSuccessor(old, nu);
        assert cnt > 0 : "B%d is not a successor of B%d".formatted(old.id, id);
        for (int i = 0; i < cnt; i++) {
            unlink(old);
            link(nu);
        }
    }

    private boolean isPlacedAtOrAbove(BasicBlock other) {
        return order != -1 && order <= other.order;
    }

    private void link(BasicBlock target) {
        if (target.isPlacedAtOrAbove(this)) {
            target.backEdgePredecessors.add(this);
        } else {
            target.predecessors.add(this);
        }
    }

    private void unlink(BasicBlock target) {
        boolean removed = target.isPlacedAtOrAbove(this)
                ? target.backEdgePredecessors.remove(this)
                : target.predecessors.remove(this);
        assert removed : "B%d is not a predecessor of B%d".formatted(id, target.id);
    }

    @Override
    public String toString() {
        return "B" + id;
    }
}
