package dev.asdf00.jluavm.vm;

import dev.asdf00.jluavm.LuaVM;
import dev.asdf00.jluavm.LuaVM.VmResult;
import dev.asdf00.jluavm.LuaVM.VmRunState;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SerializationTest extends BaseVmTest {

    @Test
    void simpleRestart() {
        var vm = LuaVM.builder().rootFunc("""
                vm.pause()
                return 1
                """).build();
        VmResult result = assertDoesNotThrow(() -> vm.run());
        assertEquals(result, VmResult.of(VmRunState.PAUSED));
        result = assertDoesNotThrow(() -> vm.runContinue());
        assertEquals(result, VmResult.of(VmRunState.SUCCESS, LuaObject.of(1)));
    }

    @Test
    void simpleDeSerialize() {
        var vm1 = LuaVM.builder().modifyEnv(g -> {
            for (var k : g.asMap().keys()) {
                if (!k.asString().equals("vm")) {
                    g.set(k, LuaObject.nil());
                }
            }
        }).rootFunc("""
                local test = 1
                vm.pause()
                return test
                """).build();
        VmResult result = assertDoesNotThrow(() -> vm1.run());
        assertEquals(result, VmResult.of(VmRunState.PAUSED));
        var state = assertDoesNotThrow(() -> vm1.serialize());
        var vm2 = assertDoesNotThrow(() -> LuaVM.builder().fromState(state).build());
        result = assertDoesNotThrow(() -> vm2.runContinue());
        assertEquals(result, VmResult.of(VmRunState.SUCCESS, LuaObject.of(1)));
    }

    @Test
    void fullDeSerialize() {
        var vm1 = LuaVM.builder().rootFunc("""
                local test = 1
                vm.pause()
                return test
                """).build();
        VmResult result = assertDoesNotThrow(() -> vm1.run());
        assertEquals(result, VmResult.of(VmRunState.PAUSED));
        var state = assertDoesNotThrow(() -> vm1.serialize());
        var vm2 = assertDoesNotThrow(() -> LuaVM.builder().fromState(state).build());
        result = assertDoesNotThrow(() -> vm2.runContinue());
        assertEquals(result, VmResult.of(VmRunState.SUCCESS, LuaObject.of(1)));
    }
}
