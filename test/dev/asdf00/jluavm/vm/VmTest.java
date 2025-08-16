package dev.asdf00.jluavm.vm;

import dev.asdf00.jluavm.LuaVM;
import dev.asdf00.jluavm.exceptions.loading.LuaParserException;
import dev.asdf00.jluavm.exceptions.loading.LuaSemanticException;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;

import static dev.asdf00.jluavm.Util.expandOptions;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unused")
public class VmTest extends BaseVmTest {
    @Test
    void simpleSnippet() {
        for (int b = -10; b < 10; b++) {
            for (var src : expandOptions("return 4+%s-(§1+0 * %s|%s * 0+1|1+0*%s|%s*0+1|1 + 0*%s|%s*0 + 1§)".formatted(b, b, b, b, b, b, b))) {
                var vm = LuaVM.builder().rootFunc(src).build();
                var res = vm.run();
                loadAssertSuccessAndRv(src, LuaObject.of(4 - 1 + b));
            }
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

    @Test
    void floorDiv() {
        // assert that LUA{a//b} == floor((float)a/(float)b) for pos and negative
        var expectedResult = new ArrayList<Double>();
        for (int a = -10; a <= 10; a++) {
            for (int b = -10; b <= 10; b++) {
                if (b == 0)
                    continue;

                var expected = Math.floor((float) a / (float) b);
                expectedResult.add(expected);
            }
        }
        for (int j = 0; j < 4; j++) {
            boolean ad = (j & 1) > 0;
            boolean bd = (j & 2) > 0;

            var code = """
                    rv = {}
                    for a=-10%s, 10 do
                       for b = -10%s, 10 do
                           if b ~= 0 then
                               rv[#rv+1] = a // b
                           end
                        end
                    end
                    return rv
                    """.formatted(ad ? ".0" : "", bd ? ".0" : "");
            var vm = LuaVM.builder().rootFunc(code).build();
            var res = vm.run();
            assertEquals(LuaVM.VmRunState.SUCCESS, res.state());
            var rvs = res.returnVars();
            assertEquals(1, rvs.length);
            var resArray = rvs[0].asMap();
            assertEquals(expectedResult.size(), resArray.luaLen());
            for (int i = 0; i < expectedResult.size(); i++) {
                var rv = resArray.getOrDefault(LuaObject.of(i + 1), null).asDouble();
                assertEquals(expectedResult.get(i), rv, 0.000001f, "ad=%s, bd=%s".formatted(ad, bd));
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
        var vm = LuaVM.builder().emptyEnv().rootFunc("return 1 + 2").build();
        var result = vm.run();
        assertEquals(new LuaVM.VmResult(LuaVM.VmRunState.SUCCESS, new LuaObject[]{LuaObject.of(3)}), result);
    }


    @Test
    public void simpleInnerFunctionWithClosure() {
        var vm = LuaVM.builder().emptyEnv().rootFunc("""
                local a = 1
                x = 2
                local function f()
                    a = a + x
                end
                f()
                return a
                """).build();
        var result = vm.run();
        assertEquals(new LuaVM.VmResult(LuaVM.VmRunState.SUCCESS, new LuaObject[]{LuaObject.of(3)}), result);
    }

    @Test
    public void simpleIPairsLoop() {
        var vm = LuaVM.builder().rootFunc("""
                local rv = "res:"
                for i, v in ipairs({1, 2, 3, nil, 5, 6}) do
                    rv = rv .. " " .. i .. "," .. v
                end
                return rv
                """).build();
        var result = vm.run();
        assertEquals(new LuaVM.VmResult(LuaVM.VmRunState.SUCCESS, new LuaObject[]{LuaObject.of("res: 1,1 2,2 3,3")}), result);
    }

    @Test
    public void simplePairsLoop() {
        // pairs iteration order is not compliant to LuaC, as this is an implementation detail according to spec
        var vm = LuaVM.builder().rootFunc("""
                local rv = "result:"
                for k, v in pairs({a = 1, 2, [1.5] = 3, "nil", bsdf = 5, 6}) do
                    rv = rv .. "\\n" .. k .. " = " .. v
                end
                return rv
                """).build();
        var result = vm.run();
        assertEquals(new LuaVM.VmResult(LuaVM.VmRunState.SUCCESS, new LuaObject[]{LuaObject.of("""
                result:
                a = 1
                1.5 = 3
                bsdf = 5
                1 = 2
                2 = nil
                3 = 6""")}), result);
    }

    @Test
    public void simpleLen() {
        var vm = LuaVM.builder().emptyEnv().rootFunc("""
                return #{1, '', nil, nil, "a", nil}
                """).build();
        var result = vm.run();
        assertEquals(new LuaVM.VmResult(LuaVM.VmRunState.SUCCESS, new LuaObject[]{LuaObject.of(5)}), result);
    }

    @Test
    public void metaLen() {
        var vm = LuaVM.builder().rootFunc("""
                x = nil
                local t = setmetatable({1, '', nil, nil, "a", nil}, {__len = function(tbl)
                        x = tbl
                        return 420
                    end})
                local len = #t
                return x == t and len
                """).build();
        var result = vm.run();
        assertEquals(new LuaVM.VmResult(LuaVM.VmRunState.SUCCESS, new LuaObject[]{LuaObject.of(420)}), result);
    }

    @Test
    public void metaMetaLookup() {
        var vm = LuaVM.builder().rootFunc("""
                local rv = "result:\\n"
                local t = setmetatable({}, {__index = {meta = "empty"}})
                rv = rv .. t.meta .. "\\n"
                local t2 = setmetatable({}, t)
                rv = rv .. tostring(t2.meta) .. "\\n"
                local t3 = setmetatable({}, {__index = t})
                rv = rv .. t3.meta .. "\\n"
                return rv
                """).build();
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
        var vm = LuaVM.builder().rootFunc("""
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
                """).build();
        var result = vm.run();
        assertEquals(new LuaVM.VmResult(LuaVM.VmRunState.SUCCESS, new LuaObject[]{LuaObject.of("""
                result:
                closing 1 ...
                closing 2 ...
                """)}), result);
    }

    @Test
    public void brokenGoto() {
        assertThrows(LuaSemanticException.class, () -> LuaVM.builder().emptyEnv().rootFunc("""
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
        var vm = LuaVM.builder().emptyEnv().rootFunc("""
                do
                    goto a
                end
                ::a::
                """).build();
        assertDoesNotThrow(vm::run);
    }

    @Test
    public void breakInlinedScope() {
        var vm = LuaVM.builder().emptyEnv().rootFunc("""
                repeat
                    do
                        break
                    end
                until true
                ::a::
                """).build();
        assertDoesNotThrow(vm::run);
    }

    @Test
    public void brokenGoto2() {
        assertDoesNotThrow(() -> LuaVM.builder().emptyEnv().rootFunc("""
                goto d
                do
                    goto d
                end
                ::d::
                """));
    }

    @Test
    public void stringExtensionFunc() {
        var bdr = LuaVM.builder()
                .emptyEnv()
                .modifyEnv(t -> t.set("_EXT", LuaObject.table(LuaObject.of("string"), LuaObject.table())));
        var vm = assertDoesNotThrow(() -> bdr.rootFunc("""
                function _EXT.string.landOf(it, arg1)
                    return "In the land of "..it.." where the "..arg1.." lie."
                end
                local mdr = ("Mordor"):landOf("shadows")
                local shr = ("the Shire"):landOf "lush grass lands"
                local gndr = "Gondor":landOf "Kingsmen"
                return mdr.."\\n"..shr.."\\n"..gndr.."\\n"
                """)).build();
        var result = vm.run();
        assertEquals(new LuaVM.VmResult(LuaVM.VmRunState.SUCCESS, new LuaObject[]{LuaObject.of("""
                In the land of Mordor where the shadows lie.
                In the land of the Shire where the lush grass lands lie.
                In the land of Gondor where the Kingsmen lie.
                """)}), result);
    }

    @Test
    public void literalExtensionCalls() {
        var vm = assertDoesNotThrow(() -> LuaVM.builder().rootFunc("""
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
                """)).build();
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
        var vm = assertDoesNotThrow(() -> LuaVM.builder().emptyEnv().rootFunc("""
                (nil)()
                """)).build();
        var res = vm.run();
        assertEquals(LuaVM.VmRunState.EXECUTION_ERROR, res.state());
    }

    @Test
    public void simpleXPCall() {
        var vm = assertDoesNotThrow(() -> LuaVM.builder().rootFunc("""
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
                """)).build();
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
        var vm = assertDoesNotThrow(() -> LuaVM.builder().rootFunc("""
                local function test(t)
                    return "received " .. tostring(t)
                end 
                return pcall(test)
                """)).build();
        var res = vm.run();
        assertEquals(LuaVM.VmResult.of(LuaVM.VmRunState.SUCCESS, LuaObject.of(true), LuaObject.of("received nil")), res);
    }

    @Test
    public void errorPCall() {
        var vm = assertDoesNotThrow(() -> LuaVM.builder().rootFunc("""
                local function test(t)
                    error("catch me")
                end 
                return pcall(test)
                """)).build();
        var res = vm.run();
        assertEquals(LuaVM.VmResult.of(LuaVM.VmRunState.SUCCESS, LuaObject.of(false), LuaObject.of("catch me")), res);
    }

    @Test
    public void simpleCoroutineTest() {
        var vm = assertDoesNotThrow(() -> LuaVM.builder().rootFunc("""
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
                """)).build();
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
        var vm = assertDoesNotThrow(() -> LuaVM.builder().rootFunc("""
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
                """)).build();
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
        var vm = LuaVM.builder().rootFunc("""
                return table.pack(...)[1]
                """).build();
        var res = vm.runWithArgs(LuaObject.of(2));
        assertEquals(
                LuaVM.VmResult.of(
                        LuaVM.VmRunState.SUCCESS,
                        LuaObject.of(2)),
                res);
    }

    @Test
    public void loadAndCallWithArgs() {
        var vm = LuaVM.builder().rootFunc("""
                local function f(...)
                    return table.pack(...)[1]
                end
                return f(42)
                """).build();
        var res = vm.run();
        assertEquals(
                LuaVM.VmResult.of(
                        LuaVM.VmRunState.SUCCESS,
                        LuaObject.of(42)),
                res);
    }

    @Test
    void monteCarloSimulationPi() {
        var rv = loadAssertSuccessGetRv("""
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
                """);
        assertTrue(rv[0].asDouble() - 3.14 < 1e-2);
    }

    @Test
    void randomNumberSeed() {
        var rv = loadAssertSuccessGetRv("""
                math.randomseed(0)
                local a = math.random()
                math.randomseed(0)
                return a - math.random()
                """);
        assertTrue(rv[0].asDouble() < 1e-12);
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

        var vm = LuaVM.builder().rootFunc(code).build();
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
        loadAssertSuccessAndRv("""
                local num = tonumber("123;")
                return num
                """, LuaObject.NIL);
        loadAssertSuccessAndRv("""
                local num = tonumber("-123.45")
                return num
                """, LuaObject.of(-123.45));
        loadAssertSuccessAndRv("""
                local num = tonumber("+123.44")
                return num
                """, LuaObject.of(123.44));
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
    void simpleNestedLoop() {
        loadAssertSuccess("""
                for i = 1, 3 do
                    for j = 4, 5 do
                    end
                end
                """);
    }

    @Test
    void nestedForRepeat() {
        loadAssertSuccess("""
                for j = 4, 5 do
                repeat
                until true
                end
                """);
    }

    @Test
    void emptyRepeatLoop() {
        loadAssertSuccess("""
                repeat until true
                """);
    }

    @Test
    void randomCode() {
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
    void reversedForLoop() {
        loadAssertSuccessAndRv("""
                s = ""
                for i = 3, 1, -1 do
                    s = s .. tostring(i)
                end
                return s
                """, LuaObject.of("321"));
    }

    @Test
    void modulo() {
        loadAssertSuccessAndRv("""
                local a, b, c = 5, 3, -8
                return (a + b * c) % 7
                """, LuaObject.of(2));
    }

    @Test
    void randomCode2() {
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

    @Test
    void luacComplianceWeirdTableConstruction() {
        loadAssertSuccessAndRv("""
                local rv = ""
                local function f(x)
                    rv = rv .. "hi:" .. x .. ";"
                    return x
                end
                local t = {f("a"), f("b"), [2] = f("override")}
                for i, v in pairs(t) do
                    rv = rv.. tostring(i) .. "-" .. v .. ";"
                end
                return rv
                """, LuaObject.of("hi:a;hi:b;hi:override;2-b;1-a;"));
    }

    @Test
    void nestedComments() {
        loadAssertSuccess("""
                --[[
                   multiline comment start
                   --[ nested comment
                   while
                   if
                   ]]
                """);
    }

    @Test
    void exceedingPrecisionLimit() {
        loadAssertSuccessAndRv("""
                local x = 1e308 * 2
                return x
                """, LuaObject.of(Double.POSITIVE_INFINITY));
    }

    @Test
    void forLoopEdgeCase1() {
        loadAssertRuntimeError("""
                for i = 1, 10, 0 do
                end
                """);
    }

    @Test
    void forLoopEdgeCase2() {
        loadAssertSuccessAndRv("""
                x = 0
                for i = 1, 10, (1/0) do
                    x = x + 1
                end
                return x
                """, LuaObject.of(1));
    }

    @Test
    void forLoopEdgeCase3() {
        loadAssertSuccessAndRv("""
                x = 0
                for i = 1, 1/0, 1/0 do
                    x = x + 1
                    if i > 10 then
                        break
                    end
                end
                return x
                """, LuaObject.of(2));
    }

    @Test
    void variableShadowingDoBlock() {
        loadAssertSuccessAndRv("""
                local x = 10
                do
                    local x = -10
                end
                return x
                """, LuaObject.of(10));
    }

    @Test
    void tableLengthTest() {
        loadAssertSuccessAndRv("""
                local t = {
                    [1] = "1",
                    [true] = "true"
                }
                return t[1] .. t[true] .. #t
                """, LuaObject.of("1true1"));
    }

    @Test
    void mixedScopeReference() {
        loadAssertSuccessAndRv("""
                local x = 10
                y = 20
                out = ""
                do
                    local y = x + y
                    out = out .. y
                end
                out = out .. y
                return out
                """, LuaObject.of("3020"));
    }

    @Test
    void excessiveArguments() {
        loadAssertSuccessAndRv("""
                local function add(a, b)
                    return a + b
                end
                x = add(1, 2, 3, 4)
                return x
                """, LuaObject.of(3));
    }

    @Test
    void ambiguousTable() {
        loadAssertSuccessAndRv("""
                local ambiguousTable = {
                    1,
                    key = 2,
                    [3] = 4,
                    "value",
                }
                return ambiguousTable[1] .. ":" .. ambiguousTable["key"] .. ":" .. ambiguousTable[3] .. ":" .. ambiguousTable[2]
                """, LuaObject.of("1:2:4:value"));
    }

    @Test
    void forLoopVariableMutation() {
        loadAssertSuccessAndRv("""
                x = 0
                for i = 1, 3 do
                    x = x + i
                    i = 100
                end
                return x
                """, LuaObject.of(6));
    }

    @Test
    void forLoopVariableShadowing() {
        loadAssertSuccessAndRv("""
                x = 0
                for i = 1, 3 do
                    for i = 1, 2 do
                        x = x + 1
                    end
                end
                return x
                """, LuaObject.of(6));
    }

    @Test
    void largeIterationLimit() {
        loadAssertSuccessAndRv("""
                local function whileLoop()
                    local count = 0
                    while true do
                        count = count + 1
                        if count >= 1e6 then
                            break
                        end
                    end
                    return count
                end
                                
                return whileLoop()
                """, LuaObject.of(1000000));
    }

    @Test
    void doBlockScopeTest() {
        loadAssertSuccessAndRv("""
                do
                    local a = 5
                    b = a + 10
                end
                return b
                """, LuaObject.of(15));
    }

    @Test
    void arithmeticEdgeCase() {
        loadAssertSuccessAndRv("""
                local x = -1e309 / 1
                return x
                """, LuaObject.of(Double.NEGATIVE_INFINITY));
    }

    @Test
    void tableModificationDuringIteration() {
        loadAssertSuccessAndRv("""
                local t = {1, 2, 3, 4}
                out = ""
                for i, v in ipairs(t) do
                    if v == 2 then
                        table.insert(t, 5)
                    end
                    out = out .. v
                end
                return out
                """, LuaObject.of("12345"));
    }

    @Test
    void mixedTable() {
        loadAssertSuccessAndRv("""
                local t = {1, 2, key1 = "value", [true] = "boolean"}
                return t[1] .. t[2] .. t.key1 .. t[true]
                """, LuaObject.of("12valueboolean"));
    }

    @Test
    void mixedFloatingPointEdgeCases() {
        loadAssertSuccessAndRv("""
                local nan = 0 / 0
                local inf = 1 / 0
                return tostring(nan == nan) .. tostring(inf + inf) .. tostring(-inf - inf) .. tostring(inf - inf)
                """, LuaObject.of("falseinf-inf-nan"));
    }

    @Test
    void xpcallTest() {
        loadAssertSuccess("""
                xpcall((0),
                    function(...)
                    end
                )
                """);
    }

    @Test
    void floorDivisionZero() {
        loadAssertRuntimeError("""
                local a = 0
                local b = 1
                local c = b // a
                """, "attempt to divide by zero");
    }

    @Test
    void floatDivisionExhaustive() {
        loadAssertSuccessAndRv("""
                local a = 10/0
                local b = 10.0/0
                local c = 10/0.0
                local d = 10.0/0.0
                local e = -10/0
                local f = -10.0/0
                local g = -10/0.0
                local h = -10.0/0.0
                local i = 10/-0
                local j = 10.0/-0
                local k = 10/-0.0
                local l = 10.0/-0.0
                local m = -10/-0
                local n = -10.0/-0
                local o = -10/-0.0
                local p = -10.0/-0.0
                return(a .. b .. c .. d .. e .. f .. g .. h .. i .. j .. k .. l .. m .. n .. o .. p)
                """, LuaObject.of("infinfinfinf-inf-inf-inf-infinfinf-inf-inf-inf-infinfinf"));
    }

    @Test
    void intDivisionExhaustive() {
        loadAssertSuccessAndRv("""
                local b = 10.0//0
                local c = 10//0.0
                local d = 10.0//0.0
                local f = -10.0//0
                local g = -10//0.0
                local h = -10.0//0.0
                local j = 10.0//-0
                local k = 10//-0.0
                local l = 10.0//-0.0
                local n = -10.0//-0
                local o = -10//-0.0
                local p = -10.0//-0.0
                return(b .. c .. d .. f .. g .. h .. j .. k .. l .. n .. o .. p)
                """, LuaObject.of("infinfinf-inf-inf-infinf-inf-inf-infinfinf"));
    }

    @Test
    void recursionTest2() {
        loadAssertSuccessAndRv("""
                local function recursion(n)
                    if n == 0 then
                        return 0
                    end
                    return recursion(n - 1)
                end
                                
                return recursion(1000000)
                """, LuaObject.of(0));
    }

    @Test
    void coroutinesAndMetatables() {
        loadAssertSuccess("""
                  local t = {}
                  local co = coroutine.create(function()
                      setmetatable(t, {
                          __index = function(_, key)
                              coroutine.yield(key)
                              return key
                          end
                      })
                      return t.some_key
                  end)
                  coroutine.resume(co)
                  coroutine.resume(co)
                """);
    }

    @Test
    void mutualRecursion() {
        loadAssertRuntimeError("""
                local function func1()
                  func2()
                end
                                
                local function func2()
                  func1()
                end
                                
                func1()
                """);
    }

    @Test
    void envManipulation() {
        loadAssertSuccessAndRv("""
                _ENV = setmetatable({}, {__index = _G})
                tostring(123)
                _ENV.tostring = nil
                return tostring(123)
                """, LuaObject.of("123"));
    }

    @Test
    void doBlockScopeTest2() {
        loadAssertSuccessAndRv("""
                y = "global"
                do
                  local x = "outer"
                  do
                      local x = "middle"
                      do
                          local x = "inner"
                          y = y .. x
                      end
                      y = y .. x
                  end
                  y = y .. x
                end
                return y
                """, LuaObject.of("globalinnermiddleouter"));
    }

    @Test
    void randomCode3() {
        loadAssertSuccessAndRv("""
                local a, b, c = 0, "hello", 42
                                
                local function f(x)
                    return (x % 2 == 0) and (x + b:len()) or (x - c)
                end
                                
                local x = f(c)
                local y = (function() return (x - c) end)()
                                
                local z = (y > 10 and y / 2) or (y * 2)
                                
                local function g(a, b)
                    return (a + b) / 3
                end
                                
                local result = g(z, b:len())
                                
                local h = function(n)
                    for i = 1, n do
                        if i == math.ceil(result) then return "final" end
                    end
                    return "ignored"
                end
                                
                return h(c % 7 + b:len())
                """, LuaObject.of("final"));
    }

    @Test
    void constantRedefinition() {
        loadAssertException("""
                local x <const> = 123
                x = nil
                """, LuaSemanticException.class);
    }

    @Test
    void constantRedefinitionAsFunction() {
        loadAssertException("""
                local x <const> = 123
                function x()
                end
                """, LuaSemanticException.class);
    }

    @Test
    void minInteger() {
        loadAssertSuccessAndRv("""
                return tostring(math.mininteger)
                """, LuaObject.of("-9223372036854775808"));
    }

    @Test
    void negativeBitShiftRight() {
        loadAssertSuccessAndRv("""
                x = 1 >> -1
                return x
                """, LuaObject.of(2));
    }

    @Test
    void negativeBitShiftLeft() {
        loadAssertSuccessAndRv("""
                x = 2 << -1
                return x
                """, LuaObject.of(1));
    }

    @Test
    void minIntegerBitShift() {
        loadAssertSuccessAndRv("""
                x = 1 << 63
                return tostring(x)
                """, LuaObject.of("-9223372036854775808"));
    }

    @Test
    void largeNumberBitShift() {
        loadAssertSuccessAndRv("""
                x = 1 << 62
                return tostring(x)
                """, LuaObject.of("4611686018427387904"));
    }

    @Test
    void bitWiseAnd() {
        loadAssertSuccessAndRv("""
                local x = 170
                local y = 204
                return x & y
                """, LuaObject.of(136));
    }

    @Test
    void bitWiseAndNegative() {
        loadAssertSuccessAndRv("""
                local x = -170
                local y = -204
                return x & y
                """, LuaObject.of(-236));
    }

    @Test
    void bitWiseOr() {
        loadAssertSuccessAndRv("""
                local x = 170
                local y = 204
                return x | y
                """, LuaObject.of(238));
    }

    @Test
    void bitWiseXor() {
        loadAssertSuccessAndRv("""
                local x = 170
                local y = 204
                return x ~ y
                """, LuaObject.of(102));
    }

    @Test
    void bitWiseXorFloat() {
        loadAssertSuccessAndRv("""
                local x = 170.0
                local y = 204.0
                return x ~ y
                """, LuaObject.of(102));
    }

    @Test
    void bitWiseXorString() {
        loadAssertRuntimeError("""
                local x = "170"
                local y = "204"
                return x ~ y
                """);
    }

    @Test
    void tableUnpack() {
        loadAssertSuccessAndRv("""
                function f(a,b,c)
                   return a .. b .. c
                end
                return (f(table.unpack({1,2,3})))
                """, new LuaObject[]{LuaObject.of("123")});
    }

    @Test
    void returnValueUnpack() {
        loadAssertSuccessAndRv("""
                local a, b = (function() return 1, 2 end)()
                return a,b
                """, new LuaObject[]{LuaObject.of(1), LuaObject.of(2)});
    }

    @Test
    void returnValueUnpack2() {
        loadAssertSuccessAndRv("""
                local t  = {(function() return 1, 2 end)()}
                return t[1],t[2]
                """, new LuaObject[]{LuaObject.of(1), LuaObject.of(2)});
    }

    @Test
    void packLengths() {
        loadAssertSuccessAndRv("return(#table.pack((function() return nil end)()))", new LuaObject[]{LuaObject.of(0)});
        loadAssertSuccessAndRv("return(#table.pack((function() return end)()))", new LuaObject[]{LuaObject.of(0)});
        loadAssertSuccessAndRv("return(#table.pack((function() return 3 end)()))", new LuaObject[]{LuaObject.of(1)});
        loadAssertSuccessAndRv("return(#table.pack((function() return nil, 3 end)()))", new LuaObject[]{LuaObject.of(2)});
        loadAssertSuccessAndRv("return(#table.pack((function() return 3, nil end)()))", new LuaObject[]{LuaObject.of(1)});
    }

    @Test
    @Timeout(5)
    void innerScopeReturn() {
        loadAssertSuccessAndRv("""
                for a,b in ipairs({1}) do
                    return false
                end
                """, new LuaObject[]{LuaObject.of(false)});
    }

    @Test
    void mathMinArrayCopyFail() {
        loadAssertSuccessAndRv("""
                return math.min(1,2)
                """, LuaObject.of(1));
    }

    @Test
    void mathMaxArrayCopyFail() {
        loadAssertSuccessAndRv("""
                return math.max(1,2)
                """, LuaObject.of(2));
    }

    @Test
    void coroutineRunningConsistency() {
        loadAssertSuccessAndRv("""
                return coroutine.running() == coroutine.running()
                """, LuaObject.TRUE);
    }

    @Test
    void emptyIfStatement() {
        loadAssertSuccessAndRv("""
                if true then end
                """, new LuaObject[0]);
    }


    @Test
    void arrayIdxRemove() {
        loadAssertSuccessAndRv("""
                t = {1,2,3,4}
                t[4] = nil
                return #t
                """, new LuaObject[]{LuaObject.of(3)});
    }

    @Test
    void stringToString() {
        loadAssertSuccessAndRv("""
                return tostring("abc")
                """, LuaObject.of("abc"));
    }

    @Test
    void stringTableConcat() {
        loadAssertSuccessAndRv("""
                local psplits = {"a","b","c"}
                return table.concat(psplits, "/", 1, #psplits-1)
                """, LuaObject.of("a/b"));
    }

    @Test
    void tableRemove() {
        loadAssertSuccessAndRv("""
                local t = {5,6,7,8,9,10}
                rv = ""
                function logTable()
                    for k,v in pairs(t) do
                        rv = rv .. k .. ":" .. v .. ";"
                    end
                    rv = rv .. ";"
                end
                logTable()
                table.remove(t,3)
                logTable()
                return rv
                """, LuaObject.of("1:5;2:6;3:7;4:8;5:9;6:10;;1:5;2:6;3:8;4:9;5:10;;"));
    }

    @Test
    void tableRemove2() {
        // this test does not aim to test pairs return order
        loadAssertSuccessAndRv("""
                local t = {1,[5]=7,nil,3}
                rv = ""
                function logTable()
                    for k,v in pairs(t) do
                        rv = rv .. k .. ":" .. v .. ";"
                    end
                    rv = rv .. ";"
                end
                logTable()
                table.remove(t,3)
                logTable()
                return rv
                """, LuaObject.of("5:7;1:1;3:3;;1:1;4:7;;"));
    }

    @Test
    void tableRemoveArgCoercion() {
        // this test does not aim to test pairs return order
        loadAssertSuccessAndRv("""
                local t = {1,2,3}
                rv = ""
                function logTable()
                    for k,v in pairs(t) do
                        rv = rv .. k .. ":" .. v .. ";"
                    end
                    rv = rv .. ";"
                end
                logTable()
                table.remove(t,"2")
                logTable()
                return rv
                """, LuaObject.of("1:1;2:2;3:3;;1:1;2:3;;"));
    }

    @Test
    void tableInsertRemoveCheck() {
        loadAssertSuccessAndRv("""
                local t = {["a"]=4}
                t["a"] = nil
                return t["a"]
                """, LuaObject.NIL);
    }

    @Test
    void tableMove() {
        loadAssertSuccessAndRv("""
                local t = {23,56,23,876,3,5,35,434,56}
                t3 = table.move(t, 1, 3, 5)
                rv = ""
                for i=-3,#t+1 do
                   rv = rv .. tostring(t3[i])..";"
                end
                return rv
                """, LuaObject.of("nil;nil;nil;nil;23;56;23;876;23;56;23;434;56;nil;"));
    }

    @Test
    void tableMove2() {
        loadAssertSuccessAndRv("""
                local t = {23,56,23,876,3,0,0,0,0}
                t3 = table.move(t, 1, "3", 7)
                rv = ""
                for i=-3,#t+1 do
                   rv = rv .. tostring(t3[i])..";"
                end
                return rv
                """, LuaObject.of("nil;nil;nil;nil;23;56;23;876;3;0;23;56;23;nil;"));
    }

    @Test
    void tableMove3() {
        loadAssertSuccessAndRv("""
                local t = {23,56,-23,876,3,7,15,5,-10}
                t3 = table.move(t, 1, "4", -2)
                rv = ""
                for i=-3,#t+1 do
                   rv = rv .. tostring(t3[i])..";"
                end
                return rv
                """, LuaObject.of("nil;23;56;-23;876;56;-23;876;3;7;15;5;-10;nil;"));
    }

    @Test
    void tableMove4() {
        loadAssertSuccessAndRv("""
                local t = {23,56,23,876,3,5,35,434,56}
                local t2 = {"a","b","c","d"}
                t3 = table.move(t, 1, 3, 5,t2)
                rv = ""
                for i=-3,#t+1 do
                   rv = rv .. tostring(t3[i])..";"
                end
                return rv
                """, LuaObject.of("nil;nil;nil;nil;a;b;c;d;23;56;23;nil;nil;nil;"));
    }

    @Test
    void mathMaxMt() {
        loadAssertSuccessAndRv("""
                mt = {["__lt"]= function(a,b) return a[1]>b[1] end}
                rv = math.max(setmetatable({5},mt), setmetatable({2},mt))
                return rv[1]
                """, LuaObject.of(2));
    }

    @Test
    void mathMinMt() {
        loadAssertSuccessAndRv("""
                mt = {["__lt"]= function(a,b) return a[1]>b[1] end}
                rv = math.min(setmetatable({5},mt), setmetatable({2},mt))
                return rv[1]
                """, LuaObject.of(5));
    }

    @Test
    void weirdMathMaxMt() {
        loadAssertSuccessAndRv("""
                mt = {["__lt"]= function(a,b) return a[1]>b[1] end}
                local function getBoxs(t)
                    rv = {}
                    for i=1,#t do
                        rv[i] = setmetatable({t[i]},mt)
                    end
                    return rv
                end
                rv = math.max(table.unpack(getBoxs({4,2,5,7,3,8,32,2,1,0,-2})), setmetatable({2},mt))
                return rv[1]
                """, LuaObject.of(2));
    }

    @Test
    void extendedMathMaxMt() {
        loadAssertSuccessAndRv("""
                mt = {["__lt"]= function(a,b) return a[1]>b[1] end}
                local function getBoxs(t)
                    rv = {}
                    for i=1,#t do
                        rv[i] = setmetatable({t[i]},mt)
                    end
                    return rv
                end
                rv = math.max(table.unpack(getBoxs({4,2,5,7,3,8,32,2,1,0,-2})))
                return rv[1]
                """, LuaObject.of(-2));
    }

    @Test
    void weirdMathMinMt() {
        loadAssertSuccessAndRv("""
                mt = {["__lt"]= function(a,b) return a[1]>b[1] end}
                local function getBoxs(t)
                    rv = {}
                    for i=1,#t do
                        rv[i] = setmetatable({t[i]},mt)
                    end
                    return rv
                end
                rv = math.min(table.unpack(getBoxs({4,2,5,7,3,8,32,2,1,0,-2})), setmetatable({2},mt))
                return rv[1]
                """, LuaObject.of(4));
    }

    @Test
    void extendedMathMinMt() {
        loadAssertSuccessAndRv("""
                mt = {["__lt"]= function(a,b) return a[1]>b[1] end}
                local function getBoxs(t)
                    rv = {}
                    for i=1,#t do
                        rv[i] = setmetatable({t[i]},mt)
                    end
                    return rv
                end
                rv = math.min(table.unpack(getBoxs({4,2,5,7,3,8,32,2,1,0,-2})))
                return rv[1]
                """, LuaObject.of(32));
    }

    @Test
    void mathMinMaxNoArg() {
        loadAssertRuntimeError("math.min()");
        loadAssertRuntimeError("math.max()");
    }

    @Test
    void mathMinMaxInvalidArgs() {
        for (int i = 0; i < 2; i++) {
            var f = "math." + (i == 0 ? "min" : "max");

            loadAssertRuntimeError(f + "(nil, true)");
            loadAssertRuntimeError(f + "(nil, nil)");
        }
    }

    @Test
    void mathMinMaxSingleNilArg() {
        loadAssertSuccessAndRv("return math.min(nil)", LuaObject.NIL);
        loadAssertSuccessAndRv("return math.max(nil)", LuaObject.NIL);
    }

    @Test
    void tableSorting2() {
        for (int i = 0; i < 3; i++)
            loadAssertSuccessAndRv("""
                            local numbers = {5,3,12,54,3,2,-15,0,1,8554,3,8,4,32,1,4,85,46,2,2,1,847,98,96,6221,1,4,488,8,5,2,3,4,4556,6,21,4,4,5,6,87,7,88,9,16,63,1,8,1}
                            table.sort(numbers%s)
                            result = ""
                            for _, v in ipairs(numbers) do
                                result = result .. v .. ","
                            end
                            return result
                            """.formatted(new String[]{", function(a, b) return a < b end", "", ", nil"}[i]),
                    LuaObject.of("-15,0,1,1,1,1,1,1,2,2,2,2,3,3,3,3,4,4,4,4,4,4,5,5,5,6,6,7,8,8,8,9,12,16,21,32,46,54,63,85,87,88,96,98,488,847,4556,6221,8554,"));
    }

    @Test
    void tableSorting3() {
        for (int i = 0; i < 3; i++)
            loadAssertSuccessAndRv("""
                            local numbers = {32,1,4,85,46}
                                table.sort(numbers%s)
                            result = ""
                            for _, v in ipairs(numbers) do
                                result = result .. v .. ","
                            end
                            return result
                            """.formatted(new String[]{", function(a, b) return a < b end", "", ", nil"}[i]),
                    LuaObject.of("1,4,32,46,85,"));
    }

    @Test
    void floorDivType() {
        var rv = loadAssertSuccessGetRv("return -10//3,-10//3.0");
        assertEquals(LuaObject.of(-4), rv[0]);
        assertEquals(LuaObject.of(-4.0), rv[1]);
    }

    @Test
    void tableSortMt() {
        loadAssertSuccessAndRv("""
                mt = {["__lt"]= function(a,b) return a[1]>b[1] end}
                local function getBoxs(t)
                    local rv = {}
                    for i=1,#t do
                        rv[i] = setmetatable({t[i]},mt)
                    end
                    return rv
                end
                rv = ""
                function logTable(t)
                    for _,v in ipairs(t) do
                        rv = rv .. v[1] .. ";"
                    end
                    rv = rv .. ";"
                end
                t = getBoxs({4,2,5,7,3,8,32,2,1,0,-2})
                logTable(t)
                table.sort(t)
                logTable(t)
                return rv
                """, LuaObject.of("4;2;5;7;3;8;32;2;1;0;-2;;32;8;7;5;4;3;2;2;1;0;-2;;"));
    }

    @Test
    void tableSetNanNil() {
        loadAssertRuntimeError("""                
                local t = {}
                local nan = 0 / 0
                t[nan] = 1
                """);
        loadAssertSuccess("""                
                local t = {}
                local inf = 1 / 0
                t[inf] = 1
                """);
        loadAssertSuccess("""                
                local t = {}
                local ninf = -1 / 0
                t[ninf] = 1
                """);
        loadAssertRuntimeError("""                
                local t = {}
                t[nil] = 1
                """);
    }

    @Test
    void tableGetNanNil() {
        loadAssertSuccessAndRv("""                
                local t = {}
                local nan = 0 / 0
                return t[nan]
                """, LuaObject.NIL);
        loadAssertSuccessAndRv("""                
                local t = {}
                local inf = 1 / 0
                return t[inf]
                """, LuaObject.NIL);
        loadAssertSuccessAndRv("""                
                local t = {}
                local ninf = -1 / 0
                return t[ninf]
                """, LuaObject.NIL);
        loadAssertSuccessAndRv("""                
                local t = {}
                return t[nil]
                """, LuaObject.NIL);
    }

    @Test
    void tableSortRv() {
        loadAssertSuccessAndRv("""
                return table.sort({4,1,2})
                """, new LuaObject[0]);
    }

    @Test
    void typeExtMethod() {
        loadAssertSuccessAndRv("""
                return "test":sub(1,3)
                """, LuaObject.of("tes"));
        loadAssertSuccessAndRv("""
                _G["_EXT"]["boolean"] = {["f"] = function(a,b) return "a"..tostring(a)..b end}
                return false:f(123)
                """, LuaObject.of("afalse123"));
    }

    @Test
    void horrificMtResume() {
        loadAssertSuccessAndRv("""
                rv = ""
                function log(x) rv = rv..";"..tostring(x) end
                                
                a = 0
                t = setmetatable({}, {["__add"] = function()
                    log("a_"..tostring(a));
                    coroutine.yield();
                    log("a2_"..tostring(a))
                    return a
                end})
                log("0")
                local co = coroutine.create(function()
                    for i=1,5 do
                       log("b"..tostring(t + i))
                       a = a * 5 + i
                    end
                end)
                while coroutine.status(co) ~= "dead" do a = a*1.5; coroutine.resume(co) end
                return rv
                """, LuaObject.of(";0;a_0.0;a2_0.0;b0.0;a_1.0;a2_1.5;b1.5;a_9.5;a2_14.25;b14.25;a_74.25;a2_111.375;b111.375;a_560.875;a2_841.3125;b841.3125"));
    }

    @Test
    void loadCallerEnv() {
        var code = """
                _G["test"] = 123
                local f = load("%s[\\"test2\\"] = 1234; return 1", "name", "t", {})()
                assert(f == 1, "return value is wrong")

                return _G["test"], _G["test2"]
                """;
        loadAssertSuccessAndRv(code.formatted("_ENV"), new LuaObject[]{LuaObject.of(123), LuaObject.NIL}); // _ENV does get set automatically -> no error
        loadAssertRuntimeError(code.formatted("_G")); // the inner env does not get _G set, therefore _G["test"] is trying to index the nil value _G
    }

    @Test
    void varArgAttemptToAccessTable() {
        var error = loadAssertRuntimeErrorGetAsString("""
                function f(...)
                    local a = ...
                    return a.abc
                end
                return f(1,2,3)
                """);

        assertEquals("Attempt to index a number value",error[0].asString());
    }

    @Test
    void shebang() {
        loadAssertSuccessAndRv("""
                #;return false
                return true
                """, LuaObject.TRUE);
    }

    @Test
    void largeIntConstParsing() {
        loadAssertSuccessAndRv("return 0x79999999", LuaObject.of(0x79999999L));
        loadAssertSuccessAndRv("return 0x80000000", LuaObject.of(0x80000000L));
        loadAssertSuccessAndRv("return 0x95cfef1f", LuaObject.of(0x95cfef1fL));
        loadAssertSuccessAndRv("return 0x8000000000000000", LuaObject.of(-9223372036854775808L));
        loadAssertSuccessAndRv("return 0x80000000000000000", LuaObject.of(0));
        loadAssertSuccessAndRv("return 9223372036854775807", LuaObject.of(9223372036854775807L));
        loadAssertSuccessAndRv("return 9223372036854775808", LuaObject.of(9223372036854775808d));
    }

    @Test
    void loopFuncsSelfClosureNotABox() {
        loadAssertSuccessAndRv("""
                if true then local a1 end
                local f -- crashes here
                f = function() return f end
                """, LuaObject.of(123));
    }
}
