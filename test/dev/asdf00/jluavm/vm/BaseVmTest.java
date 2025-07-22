package dev.asdf00.jluavm.vm;

import dev.asdf00.jluavm.LuaVM;
import dev.asdf00.jluavm.exceptions.LuaLoadingException;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import org.junit.jupiter.api.Assertions;

import java.util.Arrays;
import java.util.stream.Collectors;

import static dev.asdf00.jluavm.Util.expandOptions;
import static org.junit.jupiter.api.Assertions.*;

public abstract class BaseVmTest {
    protected static void loadAssertSuccessAndRv(String code, LuaObject expectedRet) {
        loadAssertSuccessAndRv(code, new LuaObject[]{expectedRet});
    }

    protected static LuaObject[] loadAssertSuccessGetRv(String code) {
        var vm = LuaVM.builder().rootFunc(code).build();
        var res = vm.run();
        assertEquals(LuaVM.VmRunState.SUCCESS, res.state(), () -> Arrays.stream(res.returnVars()).map(LuaObject::toString).collect(Collectors.joining()));
        return res.returnVars();
    }

    protected static void loadAssertSuccessAndRv(String code, LuaObject[] expectedRets) {
        for (var expanded : expandOptions(code)) {
            var vm = LuaVM.builder().rootFunc(expanded).build();
            var res = vm.run();
            assertEquals(LuaVM.VmResult.of(LuaVM.VmRunState.SUCCESS, expectedRets), res);
        }
    }

    protected static void loadAssertException(String s, Class<? extends LuaLoadingException> exc) {
        for (var expanded : expandOptions(s)) {
            assertThrows(exc, () -> LuaVM.builder().rootFunc(expanded).build());
        }
    }

    @SuppressWarnings("SameParameterValue")
    protected static void loadAssertRuntimeError(String s) {
        for (var expanded : expandOptions(s)) {
            LuaVM vm = assertDoesNotThrow(() -> LuaVM.builder().rootFunc(expanded).build());
            var res = assertDoesNotThrow(vm::run);
            Assertions.assertEquals(LuaVM.VmRunState.EXECUTION_ERROR, res.state());
        }
    }

    protected static LuaObject[] loadAssertRuntimeErrorGetAsString(String s) {
        LuaVM vm = assertDoesNotThrow(() -> LuaVM.builder().rootFunc(s).build());
        var res = assertDoesNotThrow(vm::run);
        Assertions.assertEquals(LuaVM.VmRunState.EXECUTION_ERROR, res.state());
        return res.returnVars();
    }

    protected static void loadAssertRuntimeError(String s, String expectedErrorMessage) {
        for (var expanded : expandOptions(s)) {
            LuaVM vm = assertDoesNotThrow(() -> LuaVM.builder().rootFunc(expanded).build());
            var res = assertDoesNotThrow(vm::run);
            var expectedVmResult = new LuaVM.VmResult(LuaVM.VmRunState.EXECUTION_ERROR, new LuaObject[]{LuaObject.of(expectedErrorMessage)});
            Assertions.assertEquals(expectedVmResult, res);
        }
    }

    protected static void loadAssertSuccess(String s) {
        for (var expanded : expandOptions(s)) {
            LuaVM vm = assertDoesNotThrow(() -> LuaVM.builder().rootFunc(expanded).build());
            var res = assertDoesNotThrow(vm::run);
            Assertions.assertEquals(LuaVM.VmRunState.SUCCESS, res.state(), () -> Arrays.stream(res.returnVars()).map(LuaObject::toString).collect(Collectors.joining()));
        }
    }
}
