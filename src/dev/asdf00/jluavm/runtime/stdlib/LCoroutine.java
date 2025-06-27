package dev.asdf00.jluavm.runtime.stdlib;

import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;
import dev.asdf00.jluavm.internals.Coroutine;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.types.AtomicLuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.Singletons;

import java.util.Arrays;

import static dev.asdf00.jluavm.runtime.utils.RTUtils.funcArgTypeError;
import static dev.asdf00.jluavm.runtime.utils.RTUtils.funcBadArgError;

public class LCoroutine {

    private static final LuaObject close = LGlobal.unimplementedFunction("coroutine.close");

    private static final LuaObject create = AtomicLuaFunction.forOneResult((vm, body) -> {
        if (!body.isFunction()) {
            vm.error(funcArgTypeError("coroutine.create", 0, body, "function"));
            return null;
        }
        return Coroutine.create(body.getFunc()).selfLuaObject;
    }).obj();

    private static final LuaObject isyieldable = LuaObject.of(new LuaFunction() {
        @Override
        public void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
            if (resume != -1) {
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
            }
            vm.registerLocals(1);
            LuaObject[] args = stackFrame[0].asArray();
            Coroutine co;
            if (args.length < 1) {
                co = vm.getCurrentCoroutine();
            } else {
                LuaObject maybeCo = args[0];
                if (!maybeCo.isThread()) {
                    vm.error(funcArgTypeError("coroutine.isyieldable", 0, maybeCo, "thread"));
                    return;
                }
                co = maybeCo.asCoroutine();
            }
            vm.returnValue(LuaObject.of(co.isYieldable));
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
            return true;
        }
    });

    private static final LuaObject resume = LuaObject.of(new LuaFunction() {
        @Override
        public void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
            if (resume == -1) {
                vm.registerLocals(2);
                LuaObject[] args = stackFrame[0].asArray();
                LuaObject maybeCo = args.length > 0 ? args[0] : null;
                if (maybeCo == null || !maybeCo.isThread()) {
                    vm.error(funcArgTypeError("coroutine.resume", 0, maybeCo, "thread"));
                    return;
                }
                Coroutine co = maybeCo.asCoroutine();
                if (co.state != Coroutine.State.SUSPENDED && co.state != Coroutine.State.CREATED) {
                    vm.error(funcBadArgError("coroutine.resume", 0, "coroutine to resume must be in 'suspended' state!"));
                    return;
                }
                stackFrame[1] = maybeCo;
                LuaObject[] passDown = args.length >= 2 ? Arrays.copyOfRange(args, 1, args.length) : Singletons.EMPTY_LUA_OBJ_ARRAY;
                setupResume(vm, co, passDown);
            } else if (resume == 0) {
                Coroutine exited = stackFrame[1].asCoroutine();
                if (exited.rootFail) {
                    vm.returnValue(LuaObject.FALSE, exited.rootReturned.length > 0 ? exited.rootReturned[0] : LuaObject.nil());
                } else {
                    // coroutine.yield stores the values to be returned by resume (by abuse of field) into exited.rootReturned
                    // even though they are not actual return values of the root function
                    var flattened = new LuaObject[exited.rootReturned.length + 1];
                    flattened[0] = LuaObject.of(true);
                    System.arraycopy(exited.rootReturned, 0, flattened, 1, exited.rootReturned.length);
                    vm.returnValue(flattened);
                }
            } else {
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
            }
        }

        @Override
        public int getMaxLocalsSize() {
            return 2;
        }

        @Override
        public int getArgCount() {
            return 1;
        }

        @Override
        public boolean hasParamsArg() {
            return true;
        }
    });

    private static final LuaObject running = AtomicLuaFunction.forManyResults(vm -> new LuaObject[]{
            vm.getCurrentCoroutine().selfLuaObject,
            LuaObject.of(vm.getCurrentCoroutine() == vm.getRootCoroutine())
    }).obj();

    private static final LuaObject status = AtomicLuaFunction.forOneResult((vm, co) -> {
        if (!co.isThread()) {
            vm.error(funcArgTypeError("coroutine.status", 0, co, "thread"));
            return null;
        }
        return LuaObject.of(co.asCoroutine().state.luaName);
    }).obj();

    private static final LuaObject wrap = AtomicLuaFunction.forOneResult((vm, func) -> {
        if (!func.isFunction()) {
            vm.error(funcArgTypeError("coroutine.resume", 0, func, "function"));
            return null;
        }
        final var co = Coroutine.create(func.getFunc());
        return LuaObject.of(new LuaFunction() {
            @Override
            public void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
                if (resume == -1) {
                    vm.registerLocals(1);
                    if (co.state != Coroutine.State.SUSPENDED && co.state != Coroutine.State.CREATED) {
                        vm.error(funcBadArgError("coroutine.wrap$resume", 0, "coroutine to resume must be in 'suspended' state!"));
                        return;
                    }
                    setupResume(vm, co, stackFrame[0].asArray());
                } else if (resume == 0) {
                    // coroutine.yield stores the values to be returned by resume (by abuse of field) into exited.rootReturned
                    // even though they are not actual return values of the root function
                    if (co.rootFail) {
                        vm.error(co.rootReturned.length > 0 ? co.rootReturned[0] : LuaObject.nil());
                    } else {
                        vm.returnValue(co.rootReturned);
                    }
                } else {
                    throw new InternalLuaRuntimeError("unknown resume point " + resume);
                }
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
                return true;
            }
        });
    }).obj();

    private static final LuaObject yield = LuaObject.of(new LuaFunction() {
        @Override
        public void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
            if (resume == -1) {
                vm.registerLocals(1);
                var co = vm.getCurrentCoroutine();
                if (!co.isYieldable) {
                    vm.error(LuaObject.of("trying to yield non-yieldable coroutine"));
                    return;
                }
                assert co.yieldTo != null : "no coroutine to yield to was found";
                // hack our own resume value
                co.luaCallStack.peek().getTopFrame().resume = 0;
                // set coroutine state
                co.state = Coroutine.State.SUSPENDED;
                // abuse rootReturned to pass return values of corresponding resume call
                co.rootReturned = stackFrame[0].asArray();
                vm.setCoroutine(co.yieldTo);
            } else if (resume == 0) {
                // resume puts the return values of the yield into the returned array
                vm.returnValue(returned);
            } else {
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
            }
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
            return true;
        }
    });

    private static void setupResume(LuaVM_RT vm, Coroutine co, LuaObject[] passDown) {
        var cur = vm.getCurrentCoroutine();
        // hack our own resume value
        cur.luaCallStack.peek().getTopFrame().resume = 0;
        // set coroutine state
        cur.state = Coroutine.State.BLOCKED;
        // retain link to resuming coroutine
        co.yieldTo = cur;
        boolean isFresh = co.state == Coroutine.State.CREATED;
        // install new coroutine
        vm.setCoroutine(co);
        if (isFresh) {
            // this coroutine has never been run before, we need to pass the arguments as true arguments to the root function
            LuaVM_RT.packArgsInto(co.luaCallStack.peek().locals, co.rootFunc, passDown);
        } else {
            // this coroutine has yielded, so we pass the given arguments as return values of the yield call
            co.luaCallStack.peek().getTopFrame().rvals = passDown;
        }
    }

    public static LuaObject getTable() {
        var tbl = LuaObject.table();
        tbl.set("close", close);
        tbl.set("create", create);
        tbl.set("isyieldable", isyieldable);
        tbl.set("resume", resume);
        tbl.set("running", running);
        tbl.set("status", status);
        tbl.set("wrap", wrap);
        tbl.set("yield", yield);
        return tbl;
    }
}
