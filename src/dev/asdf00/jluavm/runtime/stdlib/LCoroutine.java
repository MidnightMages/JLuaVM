package dev.asdf00.jluavm.runtime.stdlib;

import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;
import dev.asdf00.jluavm.internals.Coroutine;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.types.AtomicLuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;

import static dev.asdf00.jluavm.runtime.utils.RTUtils.funcArgTypeError;

public class LCoroutine {

    private static final LuaObject close = null;

    private static final LuaObject create = AtomicLuaFunction.forOneResult((vm, body) -> {
        if (!body.isFunction()) {
            vm.error(funcArgTypeError("coroutine.create", 0, body, "function"));
            return null;
        }
        return LuaObject.of(Coroutine.create(body.getFunc()));
    }).obj();

    private static final LuaObject isyieldable = LuaObject.of(new LuaFunction() {
        @Override
        public void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
            if (resume != -1) {
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
            }
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

    private static final LuaObject resume = null;

    private static final LuaObject running = null;

    private static final LuaObject status = null;

    private static final LuaObject wrap = null;

    private static final LuaObject yield = null;

}
