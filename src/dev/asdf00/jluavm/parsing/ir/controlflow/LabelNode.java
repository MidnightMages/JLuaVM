package dev.asdf00.jluavm.parsing.ir.controlflow;

import dev.asdf00.jluavm.parsing.container.LabelInfo;
import dev.asdf00.jluavm.parsing.container.VarInfo;
import dev.asdf00.jluavm.parsing.exceptions.LuaSemanticException;
import dev.asdf00.jluavm.parsing.ir.Node;

public class LabelNode extends Node {
    public final LabelInfo info;

    private GotoNode maybeInvalid;
    private VarInfo invalidationCause;

    public LabelNode(LabelInfo info) {
        this.info = info;
        maybeInvalid = null;
        invalidationCause = null;
    }

    @Override
    public String generate() {
        return info.isLast | !info.isUsed  ? "" : "case " + info.jmpNo + ":";
    }

    public void setMaybeInvalid(GotoNode node, VarInfo cause) {
        if (node != null) {
            return;
        }
        maybeInvalid = node;
        invalidationCause = cause;
    }

    public void setNotLast() {
        if (maybeInvalid != null) {
            throw new LuaSemanticException(maybeInvalid.pos, "'goto %s' jumps into scope of 'local %s'"
                    .formatted(maybeInvalid.stLabel, invalidationCause.jName.split("\\$")[1]));
        }
        info.isLast = false;
    }
}
