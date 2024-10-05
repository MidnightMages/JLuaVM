package dev.asdf00.jluavm.parsing.ir.controlflow;

import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.container.VarInfo;
import dev.asdf00.jluavm.parsing.ir.CodeGenUtils;
import dev.asdf00.jluavm.parsing.ir.Node;

public class GotoNode extends Node {
    public final Position pos;
    public final String stLabel;
    public LabelNode label;
    public final VarInfo[][] definedVars;
    public VarInfo[] toClose;

    protected GotoNode(Position pos, String stLabel, LabelNode label, VarInfo[][] definedVars, VarInfo[] toClose) {
        this.pos = pos;
        this.stLabel = stLabel;
        this.label = label;
        this.definedVars = definedVars;
        this.toClose = toClose;
    }

    public GotoNode(Position pos, String stLabel, VarInfo[][] definedVars) {
        this(pos, stLabel, null, definedVars, null);
    }

    public GotoNode(Position pos, String stLabel, LabelNode label, VarInfo[] toClose) {
        this(pos, stLabel, label, null, toClose);
    }

    @Override
    public String generate() {
        var sb = new StringBuilder();
        for (var info : toClose) {
            CodeGenUtils.genCloseVariable(sb, info);
            sb.append('\n');
        }
        if (label.info.isLast) {
            sb.append("break $goto").append(label.info.scopeId).append("_jumpLoop;");
        } else {
            sb.append("$goto").append(label.info.scopeId).append("_jumpLabel = ").append(label.info.jmpNo)
                    .append(";\ncontinue $goto").append(label.info.scopeId).append("_jumpLoop;");
        }
        return sb.toString();
    }
}
