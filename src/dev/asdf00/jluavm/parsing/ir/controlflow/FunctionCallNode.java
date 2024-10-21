package dev.asdf00.jluavm.parsing.ir.controlflow;

import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.CompilationState.EStackCallInfo;
import dev.asdf00.jluavm.parsing.ir.Node;

/**
 * This node represents a Lua function call. The default behaviour of this method is to prune the return values to only
 * the first value. If other behaviour is wanted, set an {@code expectedArgCnt} with the desired value according to
 * {@link CompilationState#generateEStackCallInfo(int)} before generating code with this node.
 */
public class FunctionCallNode extends Node {
    public final Node func;
    public final Node[] args;
    public int expectedArgCnt;

    public FunctionCallNode(Node func, Node[] args) {
        this.func = func;
        this.args = args;
        this.expectedArgCnt = 1;
    }

    @Override
    public String generate(CompilationState cState) {
        String prev = func.generate(cState) + "\n";
        for (var a : args) {
            prev += a.generate(cState) + "\n";
        }

        String[] argSpots = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            argSpots[i] = cState.popEStack();
        }
        String funcSpot = cState.popEStack();
        EStackCallInfo callInfo = cState.generateEStackCallInfo(expectedArgCnt);
        if (expectedArgCnt == -1) {
            // here we reserve space for 1 packed stack element
            cState.pushEStack();
        } else {
            for (int i = 0; i < expectedArgCnt; i++) {
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
        case %d:
        """.formatted(callInfo.saveEStack(),
                funcSpot,
                callInfo.resumeLabel(), funcSpot, stringOfArgs,
                callInfo.resumeLabel(), funcSpot, stringOfArgs,
                callInfo.resumeLabel());
        return prev + result;
    }
}
