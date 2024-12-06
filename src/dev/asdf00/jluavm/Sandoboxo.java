package dev.asdf00.jluavm;

import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.RTUtils;
import dev.asdf00.jluavm.runtime.utils.Singletons;

import java.lang.reflect.Constructor;

/**
 * <pre>
 * function (a, b)
 *   local t = x;
 *   x = a + b;
 *   local function f(a)
 *     return 1.0, t
 *   end
 *   a, y, b = f(a)
 *   if a then
 *     return a + b
 *   else
 *     b = true
 *   t = a and x
 *   return 1
 * end
 * </pre>
 */
@SuppressWarnings({"IfStatementWithIdenticalBranches", "ConstantValue", "ParameterCanBeLocal", "SwitchStatementWithTooFewBranches", "UnnecessaryLocalVariable", "DataFlowIssue", "UnusedAssignment"})
public class Sandoboxo extends LuaFunction {
    public static Constructor<? extends LuaFunction>[] innerFunctions; // populated on class load

    public Sandoboxo(LuaObject[] _ENV, LuaObject[] closures) {
        super(_ENV, closures);
    }

    @Override
    public int getMaxLocalsSize() {
        return 3;
    }

    @Override
    public int getArgCount() {
        return 2;
    }

    @Override
    public boolean hasParamsArg() {
        return false;
    }

    @Override
    public void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
        LuaObject t0 = null, t1 = null, t2 = null, t3 = null, t4 = null, t5 = null, t6 = null, t7 = null;
        // on resume
        switch (resume) {
            case -1 -> expressionStack = vm.registerExpressionStack(8);
            case 0 -> // unpack fist return value
                    t0 = returned.length > 0 ? returned[0] : LuaObject.nil();
            case 1 -> {
                // restore expression stack
                t0 = expressionStack[0];
                t1 = expressionStack[1];
                // unpack fist return value
                t2 = returned.length > 0 ? returned[0] : LuaObject.nil();
            }
            case 2 -> {
                // nothing to restore
            }
            case 3 -> {
                // restore expression stack
                t0 = expressionStack[0];
                t1 = expressionStack[1];
                t2 = expressionStack[2];
                t3 = expressionStack[3];
                t4 = expressionStack[4];
                // unpack 3 return values
                t5 = returned.length > 0 ? returned[0] : LuaObject.nil();
                t6 = returned.length > 1 ? returned[1] : LuaObject.nil();
                t7 = returned.length > 2 ? returned[2] : LuaObject.nil();
            }
            case 4 -> {
                // restore expression stack
                t0 = expressionStack[0];
                t1 = expressionStack[1];
                t2 = expressionStack[2];
                t3 = expressionStack[3];
                t4 = expressionStack[4];
            }
        }
        returned = null;
        switch (resume) {
            case -1:
                // load constant
                t0 = _ENV[0];
                // load constant
                t1 = LuaObject.of("x");
                // getExpression index
                t0 = indexedGet(vm, 0, t0, t1);
                if (t0 == null) {
                    return;
                }
                t1 = null;
            case 0:
                // assign local
                stackFrame[2] = t0;
                t0 = null;

                // load constant
                t0 = _ENV[0];
                // load constant
                t1 = LuaObject.of("x");
                // load local
                t2 = stackFrame[0];
                // load local
                t3 = stackFrame[1];
                // add
                if (t2.isArithmetic() && t3.isArithmetic()) {
                    t2 = t2.add(t3);
                } else {
                    // save expression stack
                    expressionStack[0] = t0;
                    expressionStack[1] = t1;
                    vm.callInternal(1, LuaFunction::binaryOpWithMeta, Singletons.__add, t2, t3);
                    return;
                }
            case 1:
                // set index
                if (indexedSet(vm, 2, t0, t1, t2)) {
                    return;
                }
                t2 = null;
                t1 = null;
                t0 = null;
            case 2:

                // load t
                t0 = stackFrame[2];
                // declare inner function with closure
                t0 = LuaObject.of(newInnerFunction(innerFunctions[0], _ENV, t0));
                // assign to local variable f
                stackFrame[3] = t0;
                t0 = null;

                // reserve t0 for assignment 0
                // load _ENV
                t1 = _ENV[0];
                // load "y"
                t2 = LuaObject.of("y");
                // reserve t3 for assignment 1
                // reserve t4 for assignment 2
                // load f
                t5 = stackFrame[3];
                // load a
                t6 = stackFrame[0];
                // call f(a)
                // save expression stack
                expressionStack[0] = t0;
                expressionStack[1] = t1;
                expressionStack[2] = t2;
                expressionStack[3] = t3;
                expressionStack[4] = t4;
                if (t5.isFunction()) {
                    vm.callExternal(3, t5.getFunc(), t6);
                    return;
                } else {
                    vm.callInternal(3, LuaFunction::callWithMeta, t5, t6);
                    return;
                }
            case 3:
                // shuffle local vars for assignment
                t0 = t5;
                t3 = t6;
                t4 = t7;
                t5 = null;
                t6 = null;
                t7 = null;
                // assign local variable b
                stackFrame[1] = t4;
                t4 = null;
                // set index
                if (indexedSet(vm, 4, t1, t2, t3)) {
                    expressionStack[0] = t0;
                    expressionStack[1] = t1;
                    expressionStack[2] = t2;
                    expressionStack[3] = t3;
                    expressionStack[4] = t4;
                }
                t3 = null;
                t2 = null;
                t1 = null;
            case 4:
                // assign local variable b
                stackFrame[1] = t0;
                t0 = null;

                // load local
                t0 = stackFrame[1];
                // if clause
                if (RTUtils.isTruthy(t0)) {
                    vm.callInternal(5, this::innerScope0);
                    return;
                } else {
                    vm.callInternal(5, this::innerScope1);
                    return;
                }
            case 5:

                // load a
                t0 = stackFrame[0];
                // inner resume points mapped to outer resume
            case 6:
                if (RTUtils.isTruthy(t0)) {
                    // overwrite eStack position to reenter here on resume
                    t0 = LuaObject.TRUE;
                    switch (resume) {
                        case -1, 0, 1, 2, 3, 4, 5:
                            // load constant
                            t1 = _ENV[0];
                            // load constant
                            t2 = LuaObject.of("x");
                            // getExpression index
                            t1 = indexedGet(vm, 6, t1, t2);
                            if (t0 == null) {
                                stackFrame[0] = t0;
                                return;
                            }
                            t2 = null;
                        case 6:
                    }
                    t0 = t1;
                    t1 = null;
                }
                // set local
                stackFrame[2] = t0;
                t0 = null;

                // load constant
                t0 = LuaObject.of(1);
                // return
                vm.returnValue(t0);
                return;
            default:
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
    }

    private void innerScope0(LuaVM_RT vm, LuaObject[] stackFrame, LuaObject[] args, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
        LuaObject t0 = null, t1 = null;
        switch (resume) {
            case -1 -> expressionStack = vm.registerExpressionStack(2);
        }
        returned = null;
        switch (resume) {
            case -1:
                // load local
                t0 = stackFrame[0];
                // load local
                t1 = stackFrame[1];
                // add
                if (t0.isArithmetic() && t1.isArithmetic()) {
                    t0 = t0.add(t1);
                } else {
                    vm.callInternal(0, LuaFunction::binaryOpWithMeta, Singletons.__add, t0, t1);
                    return;
                }
                t1 = null;
            case 0:
                // return
                vm.returnValue(t0);
                return;
            default:
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
    }

    private void innerScope1(LuaVM_RT vm, LuaObject[] stackFrame, LuaObject[] args, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
        LuaObject t0 = null;
        switch (resume) {
            case -1 -> expressionStack = vm.registerExpressionStack(1);
        }
        returned = null;
        switch (resume) {
            case -1:
                // load constant
                t0 = LuaObject.of(true);
                // load local
                stackFrame[1] = t0;
                // exit scope
                vm.internalReturn();
                return;
            default:
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
    }

    // in reality, this class would live in its own space and not be a mere inner member class
    @SuppressWarnings("UnusedAssignment")
    public static class InnerFunction extends LuaFunction {
        public static Constructor<? extends LuaFunction>[] innerFunctions; // populated on class load

        public InnerFunction(LuaObject[] _ENV, LuaObject... closures) {
            super(_ENV, closures);
        }

        @Override
        public int getMaxLocalsSize() {
            return 1;
        }

        @Override
        public int getArgCount() {
            return 1;
        }

        @Override
        public boolean hasParamsArg() {
            return false;
        }

        @Override
        public void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
            LuaObject t0 = null, t1 = null;
            switch (resume) {
                case -1 -> expressionStack = vm.registerExpressionStack(2);
            }
            returned = null;
            switch (resume) {
                case -1:
                    // load constant
                    t0 = LuaObject.of(1.0);
                    // load closure without box
                    t1 = closures[0];
                    // return multi value
                    vm.returnValue(t0, t1);
                    return;
                default:
                    throw new InternalLuaRuntimeError("unknown resume point " + resume);
            }
        }
    }
}
