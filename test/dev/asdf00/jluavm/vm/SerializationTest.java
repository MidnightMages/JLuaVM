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

    @Test
    void horrificCoroutine() {
        var vm1 = LuaVM.builder().rootFunc("""                
                     local root_c1 = 123
                     local root_c2 = {123}
                     local root_3 = {}
                     local root_c3 = root_3
                     rv = ""
                     local function log(...) rv = rv .. tostring(table.concat(table.pack(...),","))..";" end
                     local a = coroutine.create(function(x, y)
                         local a_c1 = 1234
                         local a_c2 = {1234}
                         local a_3 = {}
                         local a_c3 = a_3
                         local b = coroutine.create(function(x2)
                             y = y + 4
                             x2 = x2 + 5
                             local k = 4
                             coroutine.yield()
                             a_c1 = 789
                             a_c2 = {678}
                             a_c3 = {} -- new table, should affect closured var
                             y = y + k
                
                             root_c1 = 9786
                             root_c2 = {5464}
                             root_c3 = {} -- new table, should affect closured var
                             return x2
                         end)
                         coroutine.resume(b, 123)
                         log(x,y)
                         local j = 92
                         vm.pause()
                         assert(j == 92)
                         log(x,y)
                         _, co2Rv = coroutine.resume(b, 123)
                         log(x,y)
                         assert(a_c1 == 789)
                         assert(a_c2[1] == 678)
                         assert(a_c3 ~= a_3)
                         
                         assert(root_c1 == 9786)
                         assert(root_c2[1] == 5464)
                         assert(root_c3 ~= root_3)
                         return x + y + co2Rv
                     end)
                     
                     coRvT, coRv = coroutine.resume(a, 7, 47)
                     log("r",coRv)
                     return rv 
                """).build();
        VmResult result = assertDoesNotThrow(() -> vm1.run());
        assertEquals(result, VmResult.of(VmRunState.PAUSED));
        var state = assertDoesNotThrow(() -> vm1.serialize());
        var vm2 = assertDoesNotThrow(() -> LuaVM.builder().fromState(state).build());
        result = assertDoesNotThrow(() -> vm2.runContinue());
        assertEquals(result, VmResult.of(VmRunState.SUCCESS, LuaObject.of("7,51;7,51;7,55;r,190;")));
    }
}
