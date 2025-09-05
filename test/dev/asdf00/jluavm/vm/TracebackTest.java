package dev.asdf00.jluavm.vm;

import dev.asdf00.jluavm.runtime.types.LuaObject;
import org.junit.jupiter.api.Test;

public class TracebackTest extends BaseVmTest {

    @Test
    void simpleTraceback() {
        loadAssertSuccessAndRv("""
                local function f()
                    return debug.traceback()
                end
                local a
                if true then
                    a = f()
                end
                return a
                """, LuaObject.of("stack traceback:\n\tmain.lua:2: in local 'f'\n\tmain.lua:6: in main chunk"));
    }

    @Test
    void tracebackFormat() {
        loadAssertSuccessAndRv("""
                local tb
                function a(i)
                    local function b(i)
                        tb = debug.traceback()
                    end
                    if i > 0 then a(i-1) else b() end
                end
                
                a(10)
                return tb.."\\n"
                """, LuaObject.of("""
                stack traceback:
                	main.lua:4: in local 'b'
                	main.lua:6: in global 'a'
                	main.lua:6: in global 'a'
                	main.lua:6: in global 'a'
                	main.lua:6: in global 'a'
                	main.lua:6: in global 'a'
                	main.lua:6: in global 'a'
                	main.lua:6: in global 'a'
                	main.lua:6: in global 'a'
                	main.lua:6: in global 'a'
                	main.lua:6: in global 'a'
                	main.lua:6: in global 'a'
                	main.lua:9: in main chunk
                """));
    }

    @Test
    void tableBasedTracebacks() {
        loadAssertSuccessAndRv("""
                local rv = ""
                local function myPrint(toAdd)
                    rv = rv..tostring(toAdd).."\\n"
                end
                local function f()
                    return debug.traceback()
                end
                
                local g = setmetatable({asdf=f,[2]=f,[2.5]=f,[true]=f}, {
                    __add=function()
                        return debug.traceback()
                    end
                })
                myPrint(g["asdf"]())
                myPrint(g.asdf())
                myPrint(g[2]())
                myPrint(g[2.5]())
                myPrint(g[true]())
                local function x() return "asdf" end
                myPrint(g[x()]())
                myPrint(g + 1)
                
                return rv
                """, LuaObject.of("""
                stack traceback:
                	main.lua:6: in field 'asdf'
                	main.lua:14: in main chunk
                stack traceback:
                	main.lua:6: in field 'asdf'
                	main.lua:15: in main chunk
                stack traceback:
                	main.lua:6: in field 'integer index'
                	main.lua:16: in main chunk
                stack traceback:
                	main.lua:6: in field '?'
                	main.lua:17: in main chunk
                stack traceback:
                	main.lua:6: in field '?'
                	main.lua:18: in main chunk
                stack traceback:
                	main.lua:6: in field '?'
                	main.lua:20: in main chunk
                stack traceback:
                	main.lua:11: in metamethod 'add'
                	main.lua:21: in main chunk
                """));
    }

    @Test
    void functionBasedTracebacks() {
        loadAssertSuccessAndRv("""
                local rv = ""
                local function myPrint(toAdd)
                    rv = rv..tostring(toAdd).."\\n"
                end
                local function f()
                    return debug.traceback()
                end
                
                local function y() return f end
                myPrint(y()())
                
                local other = f
                myPrint(other())
                
                local g = {}
                function g:qwer()
                    return debug.traceback()
                end
                myPrint(g:qwer())
                
                return rv
                """, LuaObject.of("""
                stack traceback:
                	main.lua:6: in function <main.lua:5>
                	main.lua:10: in main chunk
                stack traceback:
                	main.lua:6: in local 'other'
                	main.lua:13: in main chunk
                stack traceback:
                	main.lua:17: in method 'qwer'
                	main.lua:19: in main chunk
                """));
    }

    @Test
    void chunkBasedTracebacks() {
        loadAssertSuccessAndRv("""
                local rv = ""
                local function myPrint(toAdd)
                    rv = rv..tostring(toAdd).."\\n"
                end
                local function f()
                    return debug.traceback()
                end
                
                local chunk = load([[
                    local function j()
                        return debug.traceback()
                    end
                    local a = j()
                    return a
                    ]], "otherChunk")
                myPrint(chunk())
                
                local c2 = load([[
                    local x = ...
                    local a = x()
                    return a
                    ]], "otherChunk2")
                myPrint(c2(f))
                
                return rv
                """, LuaObject.of("""
                stack traceback:
                	otherChunk:2: in local 'j'
                	otherChunk:4: in local 'chunk'
                	main.lua:16: in main chunk
                stack traceback:
                	main.lua:6: in local 'x'
                	otherChunk2:2: in local 'c2'
                	main.lua:23: in main chunk
                """));
    }
}
