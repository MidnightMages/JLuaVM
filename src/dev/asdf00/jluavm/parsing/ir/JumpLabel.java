package dev.asdf00.jluavm.parsing.ir;

import dev.asdf00.jluavm.parsing.container.Position;

/**
 * A jump target inside a single function. The basic block of a label is created lazily on first use, so labels can be
 * jumped to before they are placed (forward jumps).
 */
public final class JumpLabel {
    public final String name;
    public final Position pos;

    BasicBlock block;
    boolean placed;

    public JumpLabel(String name, Position pos) {
        this.name = name;
        this.pos = pos;
    }

    public JumpLabel(Position pos) {
        this(null, pos);
    }

    public boolean isPlaced() {
        return placed;
    }

    @Override
    public String toString() {
        return "JumpLabel(%s, %s)".formatted(name == null ? "<anonymous>" : name, block == null ? "-" : "B" + block.id);
    }
}
