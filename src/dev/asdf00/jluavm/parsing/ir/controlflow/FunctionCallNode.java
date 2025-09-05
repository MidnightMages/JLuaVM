package dev.asdf00.jluavm.parsing.ir.controlflow;

import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.CompilationState;
import dev.asdf00.jluavm.parsing.CompilationState.EStackCallInfo;
import dev.asdf00.jluavm.parsing.ir.Node;
import dev.asdf00.jluavm.parsing.ir.values.ConstantNode;
import dev.asdf00.jluavm.parsing.ir.values.DeRefNode;
import dev.asdf00.jluavm.parsing.ir.values.EnvAccessNode;
import dev.asdf00.jluavm.parsing.ir.values.LocalAccessNode;

/**
 * This node represents a Lua function call. The default behaviour of this method is to prune the return values to only
 * the first value. If other behaviour is wanted, set {@code expectedResultCnt} to the desired value according to
 * {@link CompilationState#generateEStackCallInfo(int)} before generating code with this node.
 */
public class FunctionCallNode extends Node {
    public final Node object;
    /**
     * This environment is only used to look up potential type extension functions.
     */
    public final Node env;
    public final Node func;
    public final Node[] args;
    public int expectedResultCnt;

    public FunctionCallNode(Position sourcePos, Node object, Node env, Node func, Node[] args) {
        super(sourcePos);
        this.object = object;
        this.env = env;
        this.func = func;
        this.args = args;
        this.expectedResultCnt = 1;
    }

    @Override
    public String generate(CompilationState cState) {
        if (args.length > 0 && args[args.length - 1] instanceof FunctionCallNode fcn) {
            fcn.expectedResultCnt = -1;
        }
        var sb = new StringBuilder();

        boolean isOopCall = object != null;
        if (isOopCall) {
            prepOopCall(sb, cState, object, env, func);
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

        String stringOfArgs = argSpots.length > 0 ? ", " + String.join(", ", argSpots) : "";
        String result = """
                %s
                vm.setLastTrace(\"%s\", %d);
                if (%s.isFunction()) vm.callExternal(%d, %s.getFunc()%s);
                else vm.callInternal(%d, LuaFunction::callWithMeta, "::callWithMeta", %s%s);
                return;
                case %d:""".formatted(callInfo.saveEStack(),
                getTraceName(), sourcePos.line(),
                funcSpot,
                callInfo.resumeLabel(), funcSpot, stringOfArgs,
                callInfo.resumeLabel(), funcSpot, stringOfArgs,
                callInfo.resumeLabel());
        return sb.append(result).toString();
    }

    public static void prepOopCall(StringBuilder sb, CompilationState cState, Node object, Node env, Node func) {
        assert func instanceof ConstantNode : "got %s as OOP call func???".formatted(func.getClass().getName());
        // generate object
        sb.append(object.generate(cState)).append('\n');
        String objSpot = cState.peekEStack();
        // duplicate object
        sb.append(cState.pushEStack()).append(" = ").append(objSpot).append(";\n");
        // generate index
        sb.append(func.generate(cState)).append('\n');
        // dereference object with index
        // here, if the dereference fails due to a type error, we do not want to eat the error immediately, we want
        // to perform a type extension function lookup instead
        sb.append(extendableDereference(cState)).append('\n');
        // swap dereferenced callable and object to pass object as first argument
        String callableRes = cState.peekEStack();
        // here we rely on the fact that func can only be an IDENT in the EBNF
        String envCode = env.generate(cState);
        String regenFuncCode = func.generate(cState);
        String funcSpot = cState.popEStack();
        String envSpot = cState.popEStack();
        cState.popEStack();
        var extCall = cState.generateEStackCallInfo(1);
        cState.pushEStack();
        // This is just the setup for an _EXT call if the previous extendableDereference failed. Here we need the _ENV.
        sb.append("""
                if (%s.isNil()) {
                    %s
                    %s
                    %s
                    vm.callInternal(%d, LuaFunction::lookupExtension, "::lookupExtension", %s, LuaObject.of(%s.getTypeAsString()), %s);
                    return;
                }
                case %d:
                """.formatted(callableRes,
                envCode,
                regenFuncCode,
                extCall.saveEStack(),
                extCall.resumeLabel(), envSpot, objSpot, funcSpot,
                extCall.resumeLabel()));
        String tmpSpot = cState.pushEStack();
        sb.append(tmpSpot).append(" = ").append(callableRes).append(";\n")
                .append(callableRes).append(" = ").append(objSpot).append(";\n")
                .append(objSpot).append(" = ").append(tmpSpot).append(";\n");
        cState.popEStack();
    }

    private static String extendableDereference(CompilationState cState) {
        String i = cState.popEStack();
        String v = cState.popEStack();
        EStackCallInfo callInfo = cState.generateEStackCallInfo(1);
        String r = cState.pushEStack();
        String result = """
                %s = %s.isExtendable() ? tryIndexedGet(vm, %d, %s, %s) : indexedGet(vm, %d, %s, %s);
                if (%s == null) {
                    %s
                    return;
                }
                case %d:""".formatted(r, v, callInfo.resumeLabel(), v, i, callInfo.resumeLabel(), v, i,
                r,
                callInfo.saveEStack(),
                callInfo.resumeLabel());
        return result;
    }

    public String getTraceName() {
        if (object != null && func instanceof ConstantNode c) {
            // oop call causes the ident to be named the 'method'
            return "method '" + c.stackTraceName + "'";
        } else if (func instanceof LocalAccessNode l) {
            // local variable calls are prefixed with 'local'
            return "local '" + l.info.baseInfo().name + "'";
        } else if (func instanceof DeRefNode d) {
            // this is a field access being called
            if (d.idx instanceof ConstantNode c) {
                if (d.value instanceof EnvAccessNode) {
                    // this is a global access
                    return "global '" + c.stackTraceName + "'";
                } else {
                    // constant field access have special names for strings and integers
                    return "field '" + c.stackTraceName + "'";
                }
            } else {
                // else, this is just an unknown field
                return "field '?'";
            }
        } else {
            // calling some other function causes the "function name" to be printed
            return "function ";
        }
    }
}
