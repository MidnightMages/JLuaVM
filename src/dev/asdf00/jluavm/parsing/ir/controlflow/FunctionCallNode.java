package dev.asdf00.jluavm.parsing.ir.controlflow;

import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.CompilationState.EStackCallInfo;
import dev.asdf00.jluavm.parsing.ir.Node;
import dev.asdf00.jluavm.parsing.ir.values.DeRefNode;

/**
 * This node represents a Lua function call. The default behaviour of this method is to prune the return values to only
 * the first value. If other behaviour is wanted, set {@code expectedResultCnt} to the desired value according to
 * {@link CompilationState#generateEStackCallInfo(int)} before generating code with this node.
 */
public class FunctionCallNode extends Node {
    public final Node object;
    public final Node func;
    public final Node[] args;
    public int expectedResultCnt;

    public FunctionCallNode(Node object, Node func, Node[] args) {
        this.object = object;
        this.func = func;
        this.args = args;
        this.expectedResultCnt = 1;
    }

    @Override
    public String generate(CompilationState cState) {
        var sb = new StringBuilder();

        boolean isOopCall = object != null;
        if (isOopCall) {
            // generate object
            sb.append(object.generate(cState)).append('\n');
            String objSpot = cState.peekEStack();
            // duplicate object
            sb.append(cState.pushEStack()).append(" = ").append(objSpot).append(";\n");
            // generate index
            sb.append(func.generate(cState)).append('\n');
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
            sb.append(func.generate(cState)).append('\n');
        }

        // generate the rest of the arguments
        for (var a : args) {
            sb.append(a.generate(cState)).append('\n');
        }

        String[] argSpots = new String[args.length + (isOopCall ? 1 : 0)];
        for (int i = argSpots.length - 1; i >= 0; i--) {
            argSpots[i] = cState.popEStack();
        }
        String funcSpot = cState.popEStack();
        EStackCallInfo callInfo = cState.generateEStackCallInfo(expectedResultCnt);
        if (expectedResultCnt == -1) {
            // here we reserve space for 1 packed stack element
            cState.pushEStack();
        } else {
            for (int i = 0; i < expectedResultCnt; i++) {
                // here we generate all stack spaces where the future return values will reside
                cState.pushEStack();
            }
        }

        String stringOfArgs = ", " + String.join(", ", argSpots);
        String result = """
                %s
                if (%s.isFunction()) {
                    vm.callExternal(%d, %s.getFunc()%s);
                    return;
                } else {
                    vm.callInternal(%d, LuaFunction::callWithMeta, %s%s);
                    return;
                }
                case %d:""".formatted(callInfo.saveEStack(),
                funcSpot,
                callInfo.resumeLabel(), funcSpot, stringOfArgs,
                callInfo.resumeLabel(), funcSpot, stringOfArgs,
                callInfo.resumeLabel());
        return sb.append(result).toString();
    }
}
