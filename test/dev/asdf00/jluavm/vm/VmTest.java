package dev.asdf00.jluavm.vm;

import dev.asdf00.jluavm.LuaVM;
import dev.asdf00.jluavm.exceptions.LuaLoadingException;
import dev.asdf00.jluavm.exceptions.loading.LuaParserException;
import dev.asdf00.jluavm.exceptions.loading.LuaSemanticException;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
            assertEquals(LuaVM.VmResult.of(LuaVM.VmRunState.SUCCESS, expectedRets), res);
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

    @Test
    void monteCarloSimulationPi() {
        loadAssertSuccessAndRv("""
                math.randomseed(123)
                local inside = 0
                local total = 0
                for i=1,100000,1 do
                    local x = (math.random()) * 2 - 1
                    local y = (math.random()) * 2 - 1
                    local d = math.sqrt(x^2 + y^2)
                    total = total + 1
                    if d < 1 then
                        inside = inside + 1
                    end
                end
                piEstimate = (inside / total) * 4
                return tostring(piEstimate)
                """, LuaObject.of("3.14072"));
    }

    @Test
    void randomNumberSeed() {
        loadAssertSuccessAndRv("""
                math.randomseed(0)
                return tostring(math.random())
                """, LuaObject.of("0.24691184196099"));
    }

    @Test
    void pseudoRandomNumberGenerator() {
        var code = """
                local seed = 123
                
                local function randomNumber()
                    local a = 1664525
                    local c = 1013904223
                    local m = 2^32
                    seed = (a * seed + c) % m
                    return seed / m
                end
                
                local result = ""
                for i = 1, 3 do
                    result = result .. "," .. tostring(randomNumber())
                end
                
                return result
                """;

        var vm = LuaVM.create().withStdLib().withRootFunc(code);
        var res = vm.run();
        assertEquals(LuaVM.VmRunState.SUCCESS, res.state());
        var rvs = res.returnVars();
        assertEquals(1, rvs.length);
        var retInts = rvs[0].asString().substring(1).split(",", 3);
        var expected = new double[]{0.28373692138121, 0.43513002363034, 0.038651257753372};
        for (int i = 0; i < 3; i++) {
            assertTrue(Double.parseDouble(retInts[i]) - expected[i] < 1e-12);
        }
    }

    @Test
    void binaryToDecimalConversion() {
        loadAssertSuccessAndRv("""
                local function binaryToDecimal(binaryString)
                    local decimal = 0
                    local length = #binaryString
                    for i=1,length do
                        -- local bit = tonumber(string.sub(binaryString, i, i))
                        local char = string.sub(binaryString, i, i)
                        local bit = nill
                        if char == "1" then
                            bit = 1
                        else
                            bit = 0
                        end
                        decimal = decimal + bit * 2^(length - i)
                    end
                    return decimal
                end

                return tostring(binaryToDecimal("101010"))
                """, LuaObject.of("42.0"));
    }

    @Test
    void toNumber() {
        loadAssertSuccessAndRv("""
                local num = tonumber("123.4")
                return num
                """, LuaObject.of(123.4));
    }

    @Test
    void mathFloor() {
        loadAssertSuccessAndRv("""
                local num = math.floor(2.7)
                return num
                """, LuaObject.of(2));
    }

    @Test
    void recursionTest() {
        loadAssertSuccessAndRv("""
                local function recursionTest(i)
                    if i <= 0 then
                        return i
                    end
                    return recursionTest(i - 1)
                end
                return recursionTest(10)
                """, LuaObject.of(0));
    }

    @Test
    void mergeSort() {
        loadAssertSuccessAndRv("""
                local function merge(arr, low, high, left, right)
                    local i, j, k = 1, 1, 1
                                
                    while i <= left and j <= right do
                        if low[i] <= high[j] then
                            arr[k] = low[i]
                            i = i + 1
                        else
                            arr[k] = high[j]
                            j = j + 1
                        end
                        k = k + 1
                    end
                                
                    while i <= left do
                        arr[k] = low[i]
                        i = i + 1
                        k = k + 1
                    end
                                
                    while j <= right do
                        arr[k] = high[j]
                        j = j + 1
                        k = k + 1
                    end
                end
                                
                local function mergeSort(arr, n)
                    if n < 2 then
                        return
                    end
                                
                    local low, high = {}, {}
                    local mid = math.floor(n / 2)
                    local left = mid
                    local right = n - mid
                                
                    -- split the array into left and right
                    for i = 1, left do
                        table.insert(low, arr[i])
                    end
                    for i = 1, right do
                        table.insert(high, arr[i + left])
                    end
                                
                    mergeSort(low, left)
                    mergeSort(high, right)
                    merge(arr, low, high, left, right)
                end
                                
                local array = {58, 75, -58, 73, -46, 77, 78, -87, 38, 71}
                local n = #array
                mergeSort(array, n)
                                
                local result = ""
                for i = 1, n do
                    result = result .. tostring(array[i]) .. ";"
                end
                return result
                """, LuaObject.of("-87;-58;-46;38;58;71;73;75;77;78;"));
    }

    @Test
    void delayedLocalShadowing() {
        loadAssertSuccessAndRv("""
                local sum = 0
                local x = 1
                do
                    local x = x + 1
                    sum = sum + x
                end
                sum = sum + x
                return sum
                """, LuaObject.of(3));
    }

    @Test
    void tableArithmeticOperation() {
        loadAssertRuntimeError("""
                local t = {}
                local x = t + 1
            """);
    }

    /*
    @Test
    void infiniteRecursion() {
        loadAssertRuntimeError("""
                local function recurse() 
                    recurse() 
                end
                recurse()
                """);
    }*/

    @Test
    void negativeSubStringArguments() {
        loadAssertSuccessAndRv("""
                return "test123":sub(3, -3)
                """, LuaObject.of("st1"));
    }

    @Test
    void chainCallsWithState() {
        loadAssertSuccessAndRv("""
                x = 0
                function outer()
                    x = x + 1
                    return outer
                end
                outer()()()
                return x
                """, LuaObject.of(3));
    }

    @Test
    void tableWithOverlappingKeys() {
        loadAssertSuccessAndRv("""
                local t = { [1]="one", ["1"]="string_one", [1.0]="float_one" }
                result = ""
                for k, v in pairs(t) do
                    result = result .. k .. v .. ";"
                end
                return result
                """, LuaObject.of("1float_one;1string_one;"));
    }

    @Test
    void unusualTableKeys() {
        loadAssertSuccess("""
                local t = {[true] = "true", [{}] = "table"}
                """);
    }

    @Test
    void arithmeticAndStringManipulations() {
        loadAssertSuccessAndRv("""
                local x = ("hello" .. 5):rep(3):sub(2, 7) .. (1 / 0)
                return x
                """, LuaObject.of("ello5hinf"));
    }

    @Test
    void implicitCoercion() {
        loadAssertSuccessAndRv("""
                local x = "5" + 3 * 2 .. "123" % 2
                return x
                """, LuaObject.of("111"));
    }

    @Test
    void arithmeticsOnBooleans() {
        loadAssertRuntimeError("""
                local x = ((1 and 0) + (not nil))
                """);
    }

    @Test
    void stringGsub() {
        loadAssertSuccessAndRv("""
                local s = ("aaa"):gsub("a", "1")
                return s
                """, LuaObject.of("111"));
    }

    @Test
    void unusualTableKeys2() {
        loadAssertSuccessAndRv("""
                local t = {[({})] = "abc", [{}] = 123}
                local ret = t[next(t)]:rep(2)
                return ret
                """, LuaObject.of("abcabc"));
    }

    @Test
    void nestedLoopsWithTable() {
        loadAssertSuccess("""
                local function test()
                    local t = { 1, 2, 3 }
                    for i = 1, #t do
                        for j = 1, #t do
                        end
                    end
                end
                test()
                """);
    }

    @Test
    void randomCode()
    {
        loadAssertSuccessAndRv("""
                local function test()
                    local a, b = 7, -3
                    local x = a * b + (b > a and 1 or 0)
                    local y = ""
                    for i = 1, 4 do
                        y = y .. ((i % 3 == 0) and "a" or "b")
                    end
                    local z = 0
                    for i = -5, 2 do
                        z = z + (i % 2 == 0 and 0 or i)
                    end
                    local q, r = {}, {}
                    for i = 1, 3 do
                        q[i] = i * i
                        r[i] = q[i] - i
                    end
                    local s = ""
                    for i = 3, 1, -1 do
                        s = s .. r[i]
                    end
                    local t = a
                    for _ = 1, 5 do
                        t = t + 1
                    end
                    local u = (t % 2 == 0 and t / 2 or t * 3)
                    return x .. y .. z .. s .. u
                end
                
                return test()
                """, LuaObject.of("-21bbab-86206.0"));
    }

    @Test
    void randomCode2()
    {
        loadAssertSuccessAndRv("""
                local function test()
                    local a, b, c = 5, 3, -8
                    local x = (a + b * c) % 7
                    local t = {}
                    for i = 1, 4 do
                        t[i] = (i % 2 == 0 and i * c or b - i * a)
                    end
                    local u = 0
                    for i = 1, 4 do
                        u = u + (t[i] > 0 and 1 or 0)
                    end
                    local v = ""
                    for i = 1, 4 do
                        v = v .. (t[i] % 2 == 0 and "+" or "-")
                    end
                    local z = {}
                    for i = -2, 2 do
                        z[i] = i * (i < 0 and b or c)
                    end
                    local w = ""
                    for i = -2, 2 do
                        w = w .. (z[i] < 0 and "n" or "p")
                    end
                    local p = 1
                    for i = 1, 3 do
                        p = p * (i + z[i - 3] + (t[4] % i))
                    end
                    local res = ((x + u) > 0 and v .. w or p)
                    return x .. u .. v .. w .. res
                end
                
                return test()
                """, LuaObject.of("20++++nnpnn++++nnpnn"));
    }

    @Test
    void localVariableShadowing() {
        loadAssertSuccessAndRv("""
                local x = 1
                local x = 2
                return x
                """, LuaObject.of(2));
    }

    @Test
    void greekVariableName() {
        loadAssertSuccessAndRv("""
                λ = 123
                return λ
                """, LuaObject.of(123));
    }

    @Test
    void invalidIdentifierTable() {
        loadAssertRuntimeError("""
                local t = { ["a.b"] = -1}
                x = t.a.b
                """);
    }

    @Test
    void mathOverride() {
        loadAssertSuccessAndRv("""
                math = {["abs"] = function(x) return x end}
                return math.abs(-1)
                """, LuaObject.of(-1));
    }

    @Test
    void variableArgumentsSumFunction() {
        loadAssertSuccessAndRv("""
                function sum(...)
                    local args = {...}
                    local total = 0
                    for _, v in ipairs(args) do
                        total = total + v
                    end
                    return total
                end
                return sum(1, 2, 3)
                """, LuaObject.of(6));
    }

    @Test
    void coroutinesTest() {
        loadAssertSuccessAndRv("""
                x = 0
                local co = coroutine.create(function()
                    for i = 1, 3 do
                        x = x + 1
                        coroutine.yield()
                    end
                end)
                
                coroutine.resume(co)
                coroutine.resume(co)
                coroutine.resume(co)
                coroutine.resume(co)
                
                return x
                """, LuaObject.of(3));
    }

    @Test
    void selfReferencingTable() {
        loadAssertSuccessAndRv("""
                local t = {}
                t.self = t
                return t.self.self.self == t
                """, LuaObject.of(true));
    }

    @Test
    void generatorsWithCoroutines() {
        loadAssertSuccessAndRv("""
                function range(start, finish, step)
                    step = step or 1
                    local current = start
                    return coroutine.wrap(function()
                        while current <= finish do
                            coroutine.yield(current)
                            current = current + step
                        end
                    end)
                end
                
                out = ""
                for n in range(1, 10, 2) do
                    out = out .. n
                end
                return out
                """, LuaObject.of("13579"));
    }

    @Test
    void tableSorting() {
        loadAssertSuccessAndRv("""
                local numbers = {5, 3, 8, 1}
                table.sort(numbers, function(a, b) return a < b end)
                result = ""
                for _, v in ipairs(numbers) do
                    result = result .. v .. ","
                end
                return result
                """, LuaObject.of("1,3,5,8,"));
    }

    @Test
    void fibonacci() {
        loadAssertSuccessAndRv("""
                local mem = {}
                function fibonacci(n)
                    if n <= 1 then
                        return n
                    end
                    if mem[n] then
                        return mem[n]
                    end
                    mem[n] = fibonacci(n - 1) + fibonacci(n - 2)
                    return mem[n]
                end
                return fibonacci(42)
                """, LuaObject.of(267914296));
    }

    @Test
    void infiniteTable() {
        loadAssertSuccessAndRv("""
                function infiniteTableGenerator()
                    local t = {}
                    setmetatable(t, {
                        __index = function(_, key)
                            return key
                        end
                    })
                    return t
                end
                
                local t = infiniteTableGenerator()
                return t[5] .. t[100] .. t["abc"]
                """, LuaObject.of("5100abc"));
    }

    @Test
    void classLikeBehavior() {
        loadAssertSuccess("""
                local Node = {}
                Node.__index = Node
                
                function Node:new(name)
                    local instance = setmetatable({}, self)
                    instance.name = name
                    instance.children = {}
                    return instance
                end
                
                function Node:addChild(child)
                    table.insert(self.children, child)
                end
                
                local parent = Node:new("parent")
                local child1 = Node:new("child1")
                local child2 = Node:new("child2")
                
                parent:addChild(child1)
                parent:addChild(child2)
                """);
    }

    @Test
    void sandboxEnv() {
        loadAssertSuccessAndRv("""
                local sandboxEnv = {
                    math = {abs = math.abs},
                }
                
                local script = [[
                    local x = -25
                    return math.abs(x)
                ]]
                
                local sandbox = load(script, "sandbox", "t", sandboxEnv)
                ret = sandbox()
                return ret
                """, LuaObject.of(25));
    }

    @Test
    void arithmeticOperatorChaining() {
        loadAssertSuccessAndRv("""
                local Chain = {}
                Chain.__index = Chain
                
                function Chain:new(value)
                    local instance = setmetatable({}, self)
                    instance.value = value
                    return instance
                end
                
                function Chain:add(x)
                    self.value = self.value + x
                    return self
                end
                
                function Chain:subtract(x)
                    self.value = self.value - x
                    return self
                end
                
                function Chain:result()
                    return self.value
                end
                
                local chain = Chain:new(10)
                return chain:add(5):subtract(3):add(2):result()
                """, LuaObject.of(14));
    }
}
