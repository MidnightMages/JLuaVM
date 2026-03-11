package dev.asdf00.jluavm.vm;

import dev.asdf00.jluavm.LuaVM;
import dev.asdf00.jluavm.LuaVM.VmResult;
import dev.asdf00.jluavm.LuaVM.VmRunState;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SerializationTest extends BaseVmTest {

    @Test
    void simpleRestart() {
        var vm = LuaVM.builder().rootFunc("""
                vm.pause()
                return 1
                """).build();
        VmResult result = assertDoesNotThrow(() -> vm.run());
        assertEquals(VmResult.of(VmRunState.PAUSED), result);
        result = assertDoesNotThrow(() -> vm.runContinue());
        assertEquals(VmResult.of(VmRunState.SUCCESS, LuaObject.of(1)), result);
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
        assertEquals(VmResult.of(VmRunState.PAUSED), result);
        var state = assertDoesNotThrow(() -> vm1.serialize());
        var vm2 = assertDoesNotThrow(() -> LuaVM.builder().fromState(state).build());
        result = assertDoesNotThrow(() -> vm2.runContinue());
        assertEquals(VmResult.of(VmRunState.SUCCESS, LuaObject.of(1)), result);
    }

    @Test
    void fullDeSerialize() {
        var vm1 = LuaVM.builder().rootFunc("""
                local test = 1
                vm.pause()
                return test
                """).build();
        VmResult result = assertDoesNotThrow(() -> vm1.run());
        assertEquals(VmResult.of(VmRunState.PAUSED), result);
        var state = assertDoesNotThrow(() -> vm1.serialize());
        var vm2 = assertDoesNotThrow(() -> LuaVM.builder().fromState(state).build());
        result = assertDoesNotThrow(() -> vm2.runContinue());
        assertEquals(VmResult.of(VmRunState.SUCCESS, LuaObject.of(1)), result);
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
        assertEquals(VmResult.of(VmRunState.PAUSED), result);
        var state = assertDoesNotThrow(() -> vm1.serialize());
        var vm2 = assertDoesNotThrow(() -> LuaVM.builder().fromState(state).build());
        result = assertDoesNotThrow(() -> vm2.runContinue());
        assertEquals(VmResult.of(VmRunState.SUCCESS, LuaObject.of("7,51;7,51;7,55;r,190;")), result);
    }

    @Test
    void coroutineWrap() {
        var vm1 = LuaVM.builder().rootFunc("""
                rv = ""
                local function log(...) rv = rv .. tostring(table.concat(table.pack(...),","))..";" end
                
                local f = function()
                    local x = 1
                    for i=1,10 do
                        log(x, i)
                        coroutine.yield()
                        x = x + 1
                        log(x, i)
                    end
                end
                local co1 = coroutine.wrap(f)
                local co2 = coroutine.wrap(f)
                vm.pause()
                co1()
                co2()
                vm.pause()
                co1()
                co2()
                return rv
                """).build();
        VmResult result = assertDoesNotThrow(() -> vm1.run());
        var box = new LuaVM[]{vm1};
        for (int i = 0; i < 2; i++) {
            assertEquals(VmResult.of(VmRunState.PAUSED), result);
            var state = assertDoesNotThrow(() -> box[0].serialize());
            box[0] = assertDoesNotThrow(() -> LuaVM.builder().fromState(state).build());
            result = assertDoesNotThrow(() -> box[0].runContinue());
        }
        assertEquals(VmResult.of(VmRunState.SUCCESS, LuaObject.of("1,1;1,1;2,1;2,2;2,1;2,2;")), result);
    }

    @Test
    void traceSerialization() {
        var vm1 = LuaVM.builder().rootFunc("""
                local rv = ""
                local function myPrint(toAdd)
                    rv = rv..tostring(toAdd).."\\n"
                end
                local function f()
                    vm.pause()
                    myPrint(debug.traceback())
                end
                
                local g = setmetatable({asdf=f,[2]=f,[2.5]=f,[true]=f}, {
                    __newindex=function(tbl,k,v)
                        myPrint("setIndex: "..k.."-"..v)
                        f()
                    end
                })
                
                local chunk = load([[
                        local tbl = ...
                        local function f(a)
                            if a < 0 then
                                tbl["test"] = "no"
                                return
                            end
                            return f(a - 1)
                        end
                        f(3)
                        ]], "innerChunk")
                chunk(g)
                
                return rv
                """).build();
        VmResult result = assertDoesNotThrow(() -> vm1.run());
        assertEquals(VmResult.of(VmRunState.PAUSED), result);
        var state = assertDoesNotThrow(() -> vm1.serialize());
        var vm2 = assertDoesNotThrow(() -> LuaVM.builder().fromState(state).build());
        result = assertDoesNotThrow(() -> vm2.runContinue());
        assertEquals(VmResult.of(VmRunState.SUCCESS, LuaObject.of("""
                setIndex: test-no
                stack traceback:
                	main.lua:7: in upvalue 'f'
                	main.lua:13: in metamethod 'newindex'
                	innerChunk:4: in function <innerChunk:2>
                	(...tail calls...)
                	innerChunk:9: in local 'chunk'
                	main.lua:28: in main chunk
                """)), result);

    }

    @Test
    void scopeSerializationWithLogging() {
        var vm1 = LuaVM.builder().rootFunc("""
                local iteration = 0
                local rv = ""
                local function printInline(x) rv = rv .. tostring(x) end
                function getMachineEvent(x)\s
                    iteration = iteration +1
                    if iteration == 2 then
                        vm.pause()
                    end
                    return "a"
                end
                
                local function readPrimitiveInput()
                    local readInput = ""
                    while iteration < 10 do
                		if true then
                		    local bla4 = 3
                		end
                        local nextEvent = table.pack(getMachineEvent())
                        if true then
                            local chr = nextEvent[1]
                            printInline(chr)
                            readInput = readInput .. chr
                        end
                    end
                end
                
                readPrimitiveInput()
                return rv
                """).build();
        VmResult result = assertDoesNotThrow(() -> vm1.run());
        assertEquals(VmResult.of(VmRunState.PAUSED), result);
        var state = assertDoesNotThrow(() -> vm1.serialize());
        var vm2 = assertDoesNotThrow(() -> LuaVM.builder().fromState(state).build());
        result = assertDoesNotThrow(() -> vm2.runContinue());
        assertEquals(VmResult.of(VmRunState.SUCCESS, LuaObject.of("aaaaaaaaaa")), result);
    }

    @Test
    void scopeSerializationSimplified() {
        var vm1 = LuaVM.builder().rootFunc("""                
                if true then
                    vm.pause()
                    if true then
                        local bla4
                    end
                    local nextEvent = 123
                    if true then
                        local chr = nextEvent + 2
                    end
                end
                """).build();
        VmResult result = assertDoesNotThrow(() -> vm1.run());
        assertEquals(VmResult.of(VmRunState.PAUSED), result);
        var state = assertDoesNotThrow(() -> vm1.serialize());
        var vm2 = assertDoesNotThrow(() -> LuaVM.builder().fromState(state).build());
        assertDoesNotThrow(() -> vm2.runContinue());
    }

    @Test
    void scopeSerializationSimplified2() {
        var vm1 = LuaVM.builder().rootFunc("""                
                if true then
                    vm.pause()
                    local nextEvent = "a"
                    if true then
                        return nextEvent
                    end
                end
                """).build();
        VmResult result = assertDoesNotThrow(() -> vm1.run());
        assertEquals(VmResult.of(VmRunState.PAUSED), result);
        var state = assertDoesNotThrow(() -> vm1.serialize());
        var vm2 = assertDoesNotThrow(() -> LuaVM.builder().fromState(state).build());
        result = assertDoesNotThrow(() -> vm2.runContinue());
        assertEquals(VmResult.of(VmRunState.SUCCESS, LuaObject.of("a")), result);
    }
}
