package dev.asdf00.jluavm.parsing.ir.controlflow;

import dev.asdf00.jluavm.parsing.container.LabelInfo;
import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.container.VarInfo;

public class GotoNode extends ClosingNode {
    public final Position pos;
    public final String stLabel;
    public LabelInfo label;
    public final VarInfo[][] definedVars;

    protected GotoNode(Position pos, String stLabel, LabelInfo label, VarInfo[][] definedVars, VarInfo[] toClose) {
        super(toClose);
        this.pos = pos;
        this.stLabel = stLabel;
        this.label = label;
        this.definedVars = definedVars;
    }

    public GotoNode(Position pos, String stLabel, VarInfo[][] definedVars) {
        this(pos, stLabel, null, definedVars, null);
    }

    public GotoNode(Position pos, String stLabel, LabelInfo label, VarInfo[] toClose) {
        this(pos, stLabel, label, null, toClose);
    }

    @Override
    public String generate() {
        var sb = new StringBuilder();
        genCloses(sb);
        if (label.isLast) {
            sb.append("break $goto").append(label.scopeId).append("_jumpLoop;");
        } else {
            sb.append("$goto_").append(label.scopeId).append("_jumpLabel = ").append(label.jmpNo)
                    .append(";\ncontinue $goto").append(label.scopeId).append("_jumpLoop;");
        }
        return sb.toString();
    }
}
