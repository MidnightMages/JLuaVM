package dev.asdf00.jluavm.vm;

import dev.asdf00.jluavm.LuaVM;
import dev.asdf00.jluavm.exceptions.LuaLoadingException;
import dev.asdf00.jluavm.exceptions.loading.LuaParserException;
import dev.asdf00.jluavm.exceptions.loading.LuaSemanticException;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static dev.asdf00.jluavm.Util.expandOptions;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unused")
public class VmTest {
    @Test
    void simpleSnippet() {
        for (int b = -10; b < 10; b++) {
            for (var src : expandOptions("return 4+%s-(§1+0 * %s|%s * 0+1|1+0*%s|%s*0+1|1 + 0*%s|%s*0 + 1§)".formatted(b, b, b, b, b, b, b))) {
                var vm = LuaVM.create().withStdLib().withRootFunc(src);
                var res = vm.run();
                loadAssertSuccessAndRv(src, LuaObject.of(4 - 1 + b));
            }
        }
    }

    private static void loadAssertSuccessAndRv(String code, LuaObject expectedRet) {
        loadAssertSuccessAndRv(code, new LuaObject[]{expectedRet});
    }

    private static void loadAssertSuccessAndRv(String code, LuaObject[] expectedRets) {
        for (var expanded : expandOptions(code)) {
            var vm = LuaVM.create().withStdLib().withRootFunc(expanded);
            var res = vm.run();
            assertEquals(LuaVM.VmRunState.SUCCESS, res.state(), () -> res.state() + " : " + Arrays.stream(res.returnVars()).map(Object::toString).collect(Collectors.joining()));
            Assertions.assertArrayEquals(expectedRets, res.returnVars());
        }
    }

    private static void loadAssertException(String s, Class<? extends LuaLoadingException> exc) {
        for (var expanded : expandOptions(s)) {
            var vm = LuaVM.create().withStdLib();
            assertThrows(exc, () -> vm.withRootFunc(expanded).run());
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static void loadAssertRuntimeError(String s) {
        for (var expanded : expandOptions(s)) {
            var vm = LuaVM.create().withStdLib();
            assertDoesNotThrow(() -> vm.withRootFunc(expanded));
            var res = assertDoesNotThrow(vm::run);
            Assertions.assertEquals(LuaVM.VmRunState.EXECUTION_ERROR, res.state());
        }
    }

    private static void loadAssertSuccess(String s) {
        for (var expanded : expandOptions(s)) {
            var vm = LuaVM.create().withStdLib();
            assertDoesNotThrow(() -> vm.withRootFunc(expanded));
            var res = assertDoesNotThrow(vm::run);
            Assertions.assertEquals(LuaVM.VmRunState.SUCCESS, res.state());
        }
    }

    @Test
    void simpleFunc() {
        loadAssertSuccessAndRv("""
                local function a(b)
                    return 5 + b§;||-b*b*0§
                end
                b = a
                return a(2) + b(3)
                """, LuaObject.of(5 + 2 + 5 + 3));
    }

    @Test
    void binOps() {
        loadAssertSuccessAndRv("return #tostring(not 2^5==false)*2>>3 ~= 1", LuaObject.FALSE);
        loadAssertSuccessAndRv("return #tostring(not 2^5==false)*2>>3 == 1", LuaObject.TRUE);
    }

    @Test
    void precedence() {
        loadAssertSuccessAndRv("""
                §local |§a = §false|nil§;
                return a and "a"<<1 or 1^0 << 0+(0 ..'0'+5^1*#{1,'',nil,nil,"a",nil})
                """, LuaObject.of(33554432));
    }

    @Test
    void assignment() {
        loadAssertSuccessAndRv("""
                a = {"hi",1,2}
                c=a
                if §true|false§ then
                a,a.b = {},7 else
                a.b,a = 7,{} end

                local rv = ""
                for _,i in ipairs({1,2,3}) do
                    rv = rv .. tostring(c[i])..","
                end
                rv = rv .. tostring(c["b"])..","..tostring(c.b)..','.. tostring(a["b"])..","..tostring(a.b)..","..tostring(#a)
                return rv
                """, LuaObject.of("hi,1,2,7,7,nil,nil,0"));
    }

    @Test
    void closure() {
        loadAssertSuccessAndRv("""
                a = 3
                b = 4
                c = function(a)
                    e = a
                    d = function(c)
                         return "("..tostring(b)..","..tostring(c)..","..tostring(e)..")"
                    end
                    e = 7
                    b = 9
                    return tostring(a)..","..tostring(b)..":"..d(8)
                end
                b = 5
                return c(7)
                """, LuaObject.of("7,9:(9,8,7)"));
    }

    @Test
    void nilCall() {
        loadAssertRuntimeError("""                
                return nonexistenttostring(123)
                """);
    }

    @Test
    void horrificSnippets() {
        // semi-resurrection
        loadAssertSuccessAndRv("""
                local a = { b = { c = 2 }}
                local x = a.b
                function f(z) z.b = 3 return 4 end
                a, a.b.c = f(a), 5
                return tostring(x.c)
                """, LuaObject.of("5"));

        // assignment order
        loadAssertSuccessAndRv("""
                rv = ""
                function f(a) rv=rv.."f;"; return 1 end
                function f1(a) rv=rv.."f1;"; return 1.1 end
                function f2(a) rv=rv.."f2;"; return 1.2 end
                function g(a) rv=rv.."g;"; return 2 end
                function h(a) rv=rv.."h;"; return 3 end

                a = {}
                setmetatable(a,a)
                a["__newindex"] = function(k,v,a)
                    rv = rv.."mt_"..tostring(v).."="..tostring(a)..";"
                    rawset(k,v,a)
                end

                a_orig = a
                a.a,a.a,a.a,a,a = f(1),f1(1),f2(1), g(1), h(1)
                rv2 = rv.."|"..tostring(a).."|"..tostring(a_orig.a)
                return rv2
                """, LuaObject.of("f;f1;f2;g;h;mt_a=1.2;|2|1"));

        loadAssertSuccessAndRv("""
                rv = ""
                function f(a) rv=rv.."f;"; return 1 end
                function f1(a) rv=rv.."f1;"; return 1.1 end
                function f2(a) rv=rv.."f2;"; return 1.2 end
                function g(a) rv=rv.."g;"; return 2 end
                function h(a) rv=rv.."h;"; return 3 end

                a = {}
                setmetatable(a,a)
                a["__newindex"] = function(k,v,a)
                    rv = rv.."mt_"..tostring(v).."="..tostring(a)..";"
                    --rawset(k,v,a)
                end

                a_orig = a
                a.a,a.a,a.a,a,a = f(1),f1(1),f2(1), g(1), h(1)
                rv2 = rv.."|"..tostring(a).."|"..tostring(a_orig.a)
                return rv2
                """, LuaObject.of("f;f1;f2;g;h;mt_a=1.2;mt_a=1.1;mt_a=1;|2|nil"));
    }

    @Test
    void label() {
        loadAssertException("""
                ::a::
                print("b")
                ::a::
                """, LuaSemanticException.class);

        loadAssertException("""
                ::a::
                print("b")
                do
                    ::a::
                    print("c")
                end
                """, LuaSemanticException.class);

        loadAssertException("""
                local TF = §true|false§
                do
                    if TF then
                        goto dest
                    end
                    local a = 1
                    §print(a)|§
                    ::dest::
                    print(1)
                end
                             
                print("ok!")
                """, LuaSemanticException.class);

        var allowedA = "adf";
        var forbiddenA = "bcez";
        var snippetA = """
                rv = ""
                local function print(e)
                    rv = tostring(e) .. "\\n"
                end
                print("b")
                local looping = true
                do
                    goto %s
                    ::a::
                    do
                        if not looping then
                            looping = true
                            goto a
                        end
                        ::b::
                        local a2 = 2
                        ::c::
                    end
                    ::d::
                    local a = 1
                    ::e::
                    print("c")
                    ::f::
                end
                print("done")
                return rv
                """;

        for (var lbl : allowedA.toCharArray()) {
            loadAssertSuccess(snippetA.formatted(lbl));
        }
        for (var lbl : forbiddenA.toCharArray()) {
            loadAssertException(snippetA.formatted(lbl), LuaSemanticException.class);
        }

        var allowedB = "abcdf";
        var forbiddenB = "ez";
        var snippetB = """
                rv = ""
                local function print(e)
                    rv = tostring(e) .. "\\n"
                end
                print("b")
                local looping = false
                do
                    goto §a|d|f§
                    ::a::
                    do
                        if not looping then
                            looping = true
                            goto %s
                        end
                        ::b::
                        local a2 = 2
                        ::c::
                    end
                    ::d::
                    local a = 1
                    ::e::
                    print("c")
                    ::f::
                end
                print("done")
                return rv
                """;

        for (var lbl : allowedB.toCharArray()) {
            loadAssertSuccess(snippetB.formatted(lbl));
        }
        for (var lbl : forbiddenB.toCharArray()) {
            loadAssertException(snippetB.formatted(lbl), LuaSemanticException.class);
        }

        loadAssertException("""
                rv = ""
                local function print(e)
                    rv = tostring(e) .. "\\n"
                end
                print("b")
                local looping = false
                do
                    goto f
                    ::a::
                    do
                        if not looping then
                            looping = true
                            goto x
                        end
                        ::b::
                        local a2 = 2
                        ::c::
                    end
                    ::d::
                    local a = 1
                    ::e::
                    print("c")
                    ::f::
                end
                print("done")
                return rv
                """, LuaSemanticException.class);

        loadAssertException("""
                do
                    function a()
                        goto lbl
                    end
                    ::lbl::
                end
                """, LuaSemanticException.class);
    }

    @SuppressWarnings("RedundantStringFormatCall")
    @Test
    void floorDiv() {
        // assert that LUA{a//b} == floor((float)a/(float)b) for pos and negative
        for (int a = -10; a < 10; a++) {
            for (int b = -10; b < 10; b++) {
                if (b == 0)
                    continue;

                System.out.println("a: %s, b:%s".formatted(a, b));
                var expected = Math.floor((float) a / (float) b);
                var vm = LuaVM.create().withStdLib().withRootFunc("return %s//%s".formatted((float) a, (float) b));
                var res = vm.run();
                assertEquals(LuaVM.VmRunState.SUCCESS, res.state());
                var rvs = res.returnVars();
                assertEquals(1, rvs.length);
                var rv = rvs[0].asDouble();
                assertEquals(expected, rv, 0.000001f);
            }
        }
    }

    @Test
    void closing() {
        loadAssertSuccessAndRv("""
                rv = ""
                do
                    local a =  {__close = function() rv=rv.."closing"..";" end}
                    local tbl = setmetatable(a,a)
                    local b <close> = a
                    local c <close> = a
                end
                        
                return rv""", LuaObject.of("closing;closing;"));

        loadAssertSuccessAndRv("""
                rv = ""
                do
                    local a1 =  {__close = function() rv=rv.."closinga1"..";" end}
                    setmetatable(a1,a1)
                    local a2 =  {__close = function() rv=rv.."closinga2"..";" end}
                    setmetatable(a2,a2)
                    local b <close> = a1
                    local c <close> = a2
                end
                return rv""", LuaObject.of("closinga2;closinga1;"));

        loadAssertSuccessAndRv("""
                rv = ""
                print = function(a) rv = rv .. tostring(a)..";" end
                      
                i = 0
                function f(x)
                    print("fval"..tostring(x))
                    i=i+1
                    return i>2
                end
                     
                function getMt()
                   local t = {["__close"]=function() print("closing") end, ["name"]="a table"}
                   setmetatable(t,t)
                   return t
                end
                          
                repeat
                    print("iter")
                    local a <close> = getMt()
                    print("b")
                until f(a.name)
                print("c")
                print("done")
                                
                return rv
                """, LuaObject.of("iter;b;fvala table;closing;iter;b;fvala table;closing;iter;b;fvala table;closing;c;done;"));

        loadAssertException("""
                §local|§ mt = {["__close"]=function() end}
                setmetatable(mt,mt)
                a <§close|const§> = mt
                """, LuaParserException.class);

        loadAssertSuccessAndRv("""
                §local|§ mt = {["__close"]=function() end}
                setmetatable(mt,mt)
                local a <§close|const§> = mt
                return "ok"
                """, LuaObject.of("ok"));
    }

    @Test
    void globals() {
        loadAssertSuccessAndRv("""
                do a = 1 end
                function b() a = 2 end
                b()
                return a
                """, LuaObject.of(2));
    }

    @Test
    public void simpleAddition() {
        var vm = LuaVM.create().withEmptyEnv().withRootFunc("return 1 + 2");
        var result = vm.run();
        assertEquals(new LuaVM.VmResult(LuaVM.VmRunState.SUCCESS, new LuaObject[]{LuaObject.of(3)}), result);
    }


    @Test
    public void simpleInnerFunctionWithClosure() {
        var vm = LuaVM.create().withEmptyEnv().withRootFunc("""
                local a = 1
                x = 2
                local function f()
                    a = a + x
                end
                f()
                return a
                """);
        var result = vm.run();
        assertEquals(new LuaVM.VmResult(LuaVM.VmRunState.SUCCESS, new LuaObject[]{LuaObject.of(3)}), result);
    }

    @Test
    public void simpleIPairsLoop() {
        var vm = LuaVM.create();
        vm.withStdLib();
        vm.withRootFunc("""
                local rv = "res:"
                for i, v in ipairs({1, 2, 3, nil, 5, 6}) do
                    rv = rv .. " " .. i .. "," .. v
                end
                return rv
                """);
        var result = vm.run();
        assertEquals(new LuaVM.VmResult(LuaVM.VmRunState.SUCCESS, new LuaObject[]{LuaObject.of("res: 1,1 2,2 3,3")}), result);
    }

    @Test
    public void simplePairsLoop() {
        var vm = LuaVM.create();
        vm.withStdLib();
        vm.withRootFunc("""
                local rv = "result:"
                for k, v in pairs({a = 1, 2, [1.5] = 3, "nil", bsdf = 5, 6}) do
                    rv = rv .. "\\n" .. k .. " = " .. v
                end
                return rv
                """);
        var result = vm.run();
        assertEquals(new LuaVM.VmResult(LuaVM.VmRunState.SUCCESS, new LuaObject[]{LuaObject.of("""
                result:
                a = 1
                1 = 2
                1.5 = 3
                2 = nil
                bsdf = 5
                3 = 6""")}), result);
    }

    @Test
    public void simpleLen() {
        var vm = LuaVM.create();
        vm.withEmptyEnv();
        vm.withRootFunc("""
                return #{1, '', nil, nil, "a", nil}
                """);
        var result = vm.run();
        assertEquals(new LuaVM.VmResult(LuaVM.VmRunState.SUCCESS, new LuaObject[]{LuaObject.of(5)}), result);
    }

    @Test
    public void metaLen() {
        var vm = LuaVM.create();
        vm.withStdLib();
        vm.withRootFunc("""
                x = nil
                local t = setmetatable({1, '', nil, nil, "a", nil}, {__len = function(tbl)
                        x = tbl
                        return 420
                    end})
                local len = #t
                return x == t and len
                """);
        var result = vm.run();
        assertEquals(new LuaVM.VmResult(LuaVM.VmRunState.SUCCESS, new LuaObject[]{LuaObject.of(420)}), result);
    }

    @Test
    public void metaMetaLookup() {
        var vm = LuaVM.create();
        vm.withStdLib();
        vm.withRootFunc("""
                local rv = "result:\\n"
                local t = setmetatable({}, {__index = {meta = "empty"}})
                rv = rv .. t.meta .. "\\n"
                local t2 = setmetatable({}, t)
                rv = rv .. tostring(t2.meta) .. "\\n"
                local t3 = setmetatable({}, {__index = t})
                rv = rv .. t3.meta .. "\\n"
                return rv
                """);
        var result = vm.run();
        assertEquals(new LuaVM.VmResult(LuaVM.VmRunState.SUCCESS, new LuaObject[]{LuaObject.of("""
                result:
                empty
                nil
                empty
                """)}), result);
    }

    @Test
    public void closeWithMetaMagic() {
        var vm = LuaVM.create();
        vm.withStdLib();
        vm.withRootFunc("""
                rv = "result:\\n"
                do
                    local cls<close> = setmetatable({}, {
                        __close = function()
                            rv = rv .. "closing 1 ...\\n"
                        end
                    })
                end
                repeat
                    local cls<close> = setmetatable({}, {
                        __close = setmetatable({}, {
                            __call = function()
                                rv = rv .. "closing 2 ...\\n"
                            end
                        })
                    })
                until true
                return rv
                """);
        var result = vm.run();
        assertEquals(new LuaVM.VmResult(LuaVM.VmRunState.SUCCESS, new LuaObject[]{LuaObject.of("""
                result:
                closing 1 ...
                closing 2 ...
                """)}), result);
    }

    @Test
    public void brokenGoto() {
        var vm = LuaVM.create();
        assertThrows(LuaSemanticException.class, () -> vm.withRootFunc("""
                do
                    goto e
                    local a = 1
                    ::e::
                    a = 2
                end
                """));
    }

    @Test
    public void gotoExitInlinedScope() {
        var vm = LuaVM.create();
        vm.withRootFunc("""
                do
                    goto a
                end
                ::a::
                """);
        assertDoesNotThrow(vm::run);
    }

    @Test
    public void breakInlinedScope() {
        var vm = LuaVM.create();
        vm.withRootFunc("""
                repeat
                    do
                        break
                    end
                until true
                ::a::
                """);
        assertDoesNotThrow(vm::run);
    }

    @Test
    public void brokenGoto2() {
        var vm = LuaVM.create();
        assertDoesNotThrow(() -> vm.withRootFunc("""
                goto d
                do
                    goto d
                end
                ::d::
                """));
    }

    @Test
    public void stringExtensionFunc() {
        var vm = LuaVM.create();
        vm.withEmptyEnv();
        vm.get_G().set("_EXT", LuaObject.table(LuaObject.of("string"), LuaObject.table()));
        assertDoesNotThrow(() -> vm.withRootFunc("""
                function _EXT.string.landOf(it, arg1)
                    return "In the land of "..it.." where the "..arg1.." lie."
                end
                local mdr = ("Mordor"):landOf("shadows")
                local shr = ("the Shire"):landOf "lush grass lands"
                local gndr = "Gondor":landOf "Kingsmen"
                return mdr.."\\n"..shr.."\\n"..gndr.."\\n"
                """));
        var result = vm.run();
        assertEquals(new LuaVM.VmResult(LuaVM.VmRunState.SUCCESS, new LuaObject[]{LuaObject.of("""
                In the land of Mordor where the shadows lie.
                In the land of the Shire where the lush grass lands lie.
                In the land of Gondor where the Kingsmen lie.
                """)}), result);
    }

    @Test
    public void literalExtensionCalls() {
        var vm = LuaVM.create();
        vm.withStdLib();
        assertDoesNotThrow(() -> vm.withRootFunc("""
                rv = ""
                function print(a)
                    rv = rv .. tostring(a) .. "\\n"
                end
                function _EXT.string.toNum(str)
                  if (str == "I") then return 1 end
                  if (str == "II") then return 2 end
                  if (str == "III") then return 3 end
                  if (str == "IV") then return 4 end
                  if (str == "V") then return 5 end
                  return nil
                end
                local romanConst<const> = {"I", "II", "III", "IV", "V"}
                function _EXT.number.toRoman(int)
                  return romanConst[int]
                end
                                
                print(5:toRoman() .. " guys")
                local myInt = ("III":toNum() + tostring("II":toNum()))
                print(myInt:toRoman())
                local four = ("V":toNum() - tostring("I":toNum())):toRoman()
                print(four)
                print(3:toRoman():toNum())
                return rv
                """));
        var result = vm.run();
        assertEquals(new LuaVM.VmResult(LuaVM.VmRunState.SUCCESS, new LuaObject[]{LuaObject.of("""
                V guys
                V
                IV
                3
                """)}), result);
    }

    @Test
    public void simpleNilCall() {
        var vm = LuaVM.create();
        assertDoesNotThrow(() -> vm.withRootFunc("""
                (nil)()
                """));
        var res = vm.run();
        assertEquals(LuaVM.VmRunState.EXECUTION_ERROR, res.state());
    }

    @Test
    public void simpleXPCall() {
        var vm = LuaVM.create().withStdLib();
        assertDoesNotThrow(() -> vm.withRootFunc("""
                pres = ""
                local function print(a)
                    pres = pres .. tostring(a) .. "\\n"
                end
                local ra, rb = xpcall(
                    function(p1)
                        print("print received: " .. tostring(p1))
                        error(99)
                    end,
                    function(err)
                        return "raised " .. tostring(err) .. " Luftballons"
                    end,
                    "i am an argument")
                return pres, ra, rb
                """));
        var res = vm.run();
        assertEquals(new LuaVM.VmResult(LuaVM.VmRunState.SUCCESS,
                new LuaObject[]{
                        LuaObject.of("print received: i am an argument\n"),
                        LuaObject.of(false),
                        LuaObject.of("raised 99 Luftballons")
                }), res);
    }

    @Test
    public void simplePCall() {
        var vm = LuaVM.create().withStdLib();
        assertDoesNotThrow(() -> vm.withRootFunc("""
                local function test(t)
                    return "received " .. tostring(t)
                end 
                return pcall(test)
                """));
        var res = vm.run();
        assertEquals(LuaVM.VmResult.of(LuaVM.VmRunState.SUCCESS, LuaObject.of(true), LuaObject.of("received nil")), res);
    }

    @Test
    public void errorPCall() {
        var vm = LuaVM.create().withStdLib();
        assertDoesNotThrow(() -> vm.withRootFunc("""
                local function test(t)
                    error("catch me")
                end 
                return pcall(test)
                """));
        var res = vm.run();
        assertEquals(LuaVM.VmResult.of(LuaVM.VmRunState.SUCCESS, LuaObject.of(false), LuaObject.of("catch me")), res);
    }

    @Test
    public void simpleCoroutineTest() {
        var vm = LuaVM.create().withStdLib();
        assertDoesNotThrow(() -> vm.withRootFunc("""
                pres = ""
                local function print(a)
                    pres = pres .. tostring(a) .. "\\n"
                end
                local co = coroutine.create(
                    function (a)
                        print("initial received: " .. tostring(a))
                        local b = coroutine.yield(1)
                        print("post-yield received: " .. tostring(b))
                        return 2
                    end)
                local state1, one = coroutine.resume(co, "first")
                local state2, two = coroutine.resume(co, "second")
                return pres, state1, one, state2, two
                """));
        var res = vm.run();
        assertEquals(
                LuaVM.VmResult.of(LuaVM.VmRunState.SUCCESS,
                        LuaObject.of("""
                                initial received: first
                                post-yield received: second
                                """),
                        LuaObject.of(true),
                        LuaObject.of(1),
                        LuaObject.of(true),
                        LuaObject.of(2)),
                res);
    }

    @Test
    public void wrappedCoroutineTest() {
        var vm = LuaVM.create().withStdLib();
        assertDoesNotThrow(() -> vm.withRootFunc("""
                pres = ""
                local function print(a)
                    pres = pres .. tostring(a) .. "\\n"
                end
                local wCo = coroutine.wrap(
                    function (a)
                        print("initial received: " .. tostring(a))
                        local b = coroutine.yield(1)
                        print("post-yield received: " .. tostring(b))
                        error("i am an error")
                    end)
                local one = wCo("first")
                local state, msg = pcall(wCo, "second")
                return pres, one, state, msg
                """));
        var res = vm.run();
        assertEquals(
                LuaVM.VmResult.of(LuaVM.VmRunState.SUCCESS,
                        LuaObject.of("""
                                initial received: first
                                post-yield received: second
                                """),
                        LuaObject.of(1),
                        LuaObject.of(false),
                        LuaObject.of("i am an error")),
                res);
    }

    @Test
    public void runWithArgs() {
        var vm = LuaVM.create().withStdLib().withRootFunc("""
                return table.pack(...)[1]
                """);
        var res = vm.runWithArgs(LuaObject.of(2));
        assertEquals(
                LuaVM.VmResult.of(
                        LuaVM.VmRunState.SUCCESS,
                        LuaObject.of(2)),
                res);
    }

    @Test
    public void loadAndCallWithArgs() {
        var vm = LuaVM.create().withStdLib().withRootFunc("""
                local function f(...)
                    return table.pack(...)[1]
                end
                return f(42)
                """);
        var res = vm.run();
        assertEquals(
                LuaVM.VmResult.of(
                        LuaVM.VmRunState.SUCCESS,
                        LuaObject.of(42)),
                res);
    }


}
