package dev.asdf00.jluavm.parsing.ir.controlflow;

import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.Node;
import dev.asdf00.jluavm.parsing.ir.values.DeRefNode;

public class TailCallNode extends Node {
    FunctionCallNode call;

    public TailCallNode(Position sourcePos, FunctionCallNode call) {
        super(sourcePos);
        this.call = call;
    }

    @Override
    public String generate(CompilationState cState) {
        assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        var sb = new StringBuilder();

        boolean isOopCall = call.object != null;
        if (isOopCall) {
            // generate object
            sb.append(call.object.generate(cState)).append('\n');
            String objSpot = cState.peekEStack();
            // duplicate object
            sb.append(cState.pushEStack()).append(" = ").append(objSpot).append(";\n");
            // generate index
            sb.append(call.func.generate(cState)).append('\n');
            // dereference object with index
            sb.append(DeRefNode.dereference(cState)).append('\n');
            // swap dereferenced callable and object to pass object as first argument
            String callableRes = cState.peekEStack();
            String tmpSpot = cState.pushEStack();
            sb.append(tmpSpot).append(" = ").append(callableRes).append(";\n")
                    .append(callableRes).append(" = ").append(objSpot).append(";\n")
                    .append(objSpot).append(" = ").append(tmpSpot).append(";\n");
            cState.popEStack();
        } else {
            sb.append(call.func.generate(cState)).append('\n');
        }

        // generate the rest of the arguments
        for (var a : call.args) {
            sb.append(a.generate(cState)).append('\n');
        }

        String[] argSpots = new String[call.args.length + (isOopCall ? 1 : 0)];
        for (int i = argSpots.length - 1; i >= 0; i--) {
            argSpots[i] = cState.popEStack();
        }
        String funcSpot = cState.popEStack();
        assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        CompilationState.EStackCallInfo callInfo = cState.generateEStackCallInfo(-1);
        // here we reserve space for 1 packed stack element
        String rval = cState.pushEStack();
        cState.popEStack();
        assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        String stringOfArgs = argSpots.length > 0 ? ", " + String.join(", ", argSpots) : "";
        String result = """
                if (%s.isFunction()) vm.tailCall(%s.getFunc()%s);
                else vm.callInternal(%d, LuaFunction::callWithMeta, %s%s);
                return;
                case %d:
                vm.returnValue(%s);
                if (true) return;""".formatted(
                funcSpot, funcSpot, stringOfArgs,
                callInfo.resumeLabel(), funcSpot, stringOfArgs,
                callInfo.resumeLabel(),
                rval);
        return sb.append(result).toString();
    }
}
