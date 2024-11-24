package dev.asdf00.jluavm.parsing.ir.controlflow;

import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.IRBlock;
import dev.asdf00.jluavm.parsing.ir.Node;
import dev.asdf00.jluavm.utils.Triple;

import java.util.ArrayList;

public class IfNode extends Node {
    public final Node cond;
    public final IRBlock then;
    public final Node[] subConds;
    public final IRBlock[] elseIfs;
    public final IRBlock _else;

    public IfNode(Position sourcePos, Node cond, IRBlock then) {
        this(sourcePos, cond, then, new Node[0], null, null);
    }

    public IfNode(Position sourcePos, Node cond, IRBlock then, Node[] subConds, IRBlock[] elseIfs, IRBlock _else) {
        super(sourcePos);
        this.cond = cond;
        this.then = then;
        this.subConds = subConds;
        this.elseIfs = elseIfs;
        this._else = _else;
    }

    @Override
    public String generate(CompilationState cState) {
        // generate sub-code
        // condition code, eStack spot of condition result, block name
        var conditions = new ArrayList<Triple<String, String, String>>();
        conditions.add(new Triple<>(cond.generate(cState), cState.popEStack(), then.generate(cState)));
        for (int i = 0; i < subConds.length; i++) {
            conditions.add(new Triple<>(subConds[i].generate(cState), cState.popEStack(), elseIfs[i].generate(cState)));
        }
        String elseBlockName = _else == null ? null : _else.generate(cState);
        assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        var callInfo = cState.generateEStackCallInfo(0);

        // build code for if from generated elements
        var sb = new StringBuilder();
        assert !conditions.isEmpty();
        for (int i = 0; i < conditions.size(); i++) {
            var cc = conditions.get(i);
            sb.append("""
                    %s
                    if (RTUtils.isTruthy(%s)) {
                        vm.callInternal(%d, this::%s);
                        return;
                    }
                    """.formatted(cc.x(), cc.y(), callInfo.resumeLabel(), cc.z()));
        }
        if (elseBlockName != null) {
            sb.append("""
                    vm.callInternal(%d, this::%s);
                    return;
                    """.formatted(callInfo.resumeLabel(), elseBlockName));
        }
        sb.append("case ").append(callInfo.resumeLabel()).append(':');
        return sb.toString();
    }
}
