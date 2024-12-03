package dev.asdf00.jluavm.lualoaded;

import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;
import dev.asdf00.jluavm.exceptions.LuaRuntimeError;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.errors.*;
import dev.asdf00.jluavm.runtime.types.*;
import dev.asdf00.jluavm.runtime.utils.*;

import java.lang.reflect.Constructor;
import java.util.Arrays;

public final class GeneratedLuaFunc_0 extends LuaFunction {
public static Constructor<? extends LuaFunction>[] innerFunctions;

public GeneratedLuaFunc_0(LuaObject[] _ENV, LuaObject[] closures) {
    super(_ENV, closures);
}

@Override
public int getMaxLocalsSize() {
    return 0;
}

@Override
public int getArgCount() {
    return 0;
}

@Override
public boolean hasParamsArg() {
    return false;
}

@Override
public void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
LuaObject t0 = null, t1 = null;
switch (resume) {
case -1 -> {
    expressionStack = null;
    vm.registerLocals(0);
}

case 0 -> {
    // nothing to restore
    t0 = returned.length > 0 ? returned[0] : LuaObject.nil();
}
default -> throw new InternalLuaRuntimeError("unknown resume point " + resume);
}
returned = null;
switch (resume) {
case -1:
t0 = LuaObject.of(1);
t1 = LuaObject.of(2);
if (t0.isNumberCoercible() && t1.isNumberCoercible()) {
    t0 = t0.add(t1);
} else {
    // nothing to save
    vm.callInternal(0, LuaFunction::binaryOpWithMeta, Singletons.__add, t0, t1);
    return;
}
case 0:
// nothing to close
vm.returnValue(t0);
if (true) return;
debugPoint("at lua l1:c0", _ENV, stackFrame, vm);
// nothing to close
vm.returnValue();
return;
default: throw new InternalLuaRuntimeError("should not reach end of fall-through switch");
}
}

// inner scopes

}