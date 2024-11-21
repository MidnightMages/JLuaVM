package dev.asdf00.jluavm.parsing.ir;

import dev.asdf00.jluavm.parsing.container.Position;

public class SequenceNode extends Node {
    public final Node[] sequence;

    public SequenceNode(Position sourcePos, Node... sequence) {
        super(sourcePos);
        this.sequence = sequence;
    }

    @Override
    public String generate(CompilationState cState) {
        if (sequence.length < 1) {
            return "// empty sequence";
        }
        var sb = new StringBuilder();
        sb.append(sequence[0].generate(cState));
        for (int i = 1; i < sequence.length; i++) {
            sb.append('\n').append(sequence[i].generate(cState));
        }
        return sb.toString();
    }
}
