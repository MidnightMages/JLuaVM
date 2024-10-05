package dev.asdf00.jluavm.vm;

import dev.asdf00.jluavm.LuaVM;
import dev.asdf00.jluavm.exceptions.loading.LuaParserException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static dev.asdf00.jluavm.Util.expandOptions;

public class VmTest {
    @Test
    void simpleSnippet() {
        for (int b = -10; b < 10; b++) {
            for (var src : expandOptions("return 4+%s-(§1+0 * %s|%s * 0+1|1+0*%s|%s*0+1|1 + 0*%s|%s*0 + 1§)".formatted(b, b, b, b, b, b, b))) {
                var vm = new LuaVM();
                vm.load(src);
                var res = vm.run();
                Assertions.assertEquals(LuaVM.VmRunState.SUCCESS, res.state());
                Assertions.assertArrayEquals(new Object[]{4 - 1 + b}, res.returnVars());
            }
        }
    }

    private static void loadAssertSuccessAndRv(String code, Object[] expectedRets) {
        for (var expanded : expandOptions(code)) {
            var vm = new LuaVM();
            vm.load(expanded);
            var res = vm.run();
            Assertions.assertEquals(LuaVM.VmRunState.SUCCESS, res.state());
            Assertions.assertArrayEquals(expectedRets, res.returnVars());
        }
    }

    private static void loadAssertException(String s, Class<? extends LuaParserException> exc) {
        for (var expanded : expandOptions(s)) {
            var vm = new LuaVM();
            Assertions.assertThrows(exc, () -> vm.load(expanded));
            //vm.run();
        }
    }

    private static void loadAssertSuccess(String s) {
        for (var expanded : expandOptions(s)) {
            var vm = new LuaVM();
            Assertions.assertDoesNotThrow(() -> vm.load(expanded));
            Assertions.assertDoesNotThrow(vm::run);
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
                """, new Object[]{5 + 2 + 5 + 3});
    }

    @Test
    void binOps() {
        loadAssertSuccessAndRv("return #tostring(not 2^5==false)*2>>3 ~= 1", new Object[]{false});
        loadAssertSuccessAndRv("return #tostring(not 2^5==false)*2>>3 == 1", new Object[]{true});
    }

    @Test
    void precedence() {
        loadAssertSuccessAndRv("""
                §local |§a = §false|nil§;
                return a and "a"<<1 or 1^0 << 0+(0 ..'0'+5^1*#{1,'',nil,nil,"a",nil})
                """, new Object[]{33554432});
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
                """, new Object[]{"hi,1,2,7,7,nil,nil,0"});
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
                """, new Object[]{"7,9:(9,8,7)"});
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
                """, new Object[]{"5"});

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
                """, new Object[]{"f;f1;f2;g;h;mt_a=1.2;|2|1"});

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
                """, new Object[]{"f;f1;f2;g;h;mt_a=1.2;mt_a=1.1;mt_a=1;|2|nil"});
    }

    @Test
    void label() {
        loadAssertException("""
                ::a::
                print("b")
                ::a::
                """, LuaParserException.class);

        loadAssertException("""
                ::a::
                print("b")
                do
                    ::a::
                    print("c")
                end
                """, LuaParserException.class);

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
                """, LuaParserException.class);
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
                """, LuaParserException.class);

        var allowedA = "adf";
        var forbiddenA = "bcez";
        var snippetA = """
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
                """;

        for (var lbl : allowedA.toCharArray()) {
            loadAssertSuccess(snippetA.formatted(lbl));
        }
        for (var lbl : forbiddenA.toCharArray()) {
            loadAssertException(snippetA.formatted(lbl), LuaParserException.class);
        }

        var allowedB = "abcdf";
        var forbiddenB = "ez";
        var snippetB = """                
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
                """;

        for (var lbl : allowedB.toCharArray()) {
            loadAssertSuccess(snippetB.formatted(lbl));
        }
        for (var lbl : forbiddenB.toCharArray()) {
            loadAssertException(snippetB.formatted(lbl), LuaParserException.class);
        }

        loadAssertException("""
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
                """, LuaParserException.class);

        loadAssertException("""
                do
                    function a()
                        goto lbl
                    end
                    ::lbl::
                end
                """, LuaParserException.class);
    }

    @Test
    void floorDiv() {
        // assert that LUA{a//b} == floor((float)a/(float)b) for pos and negative
        for (int a = -10; a < 10; a++) {
            for (int b = -10; b < 10; b++) {
                if (b == 0)
                    continue;

                var expected = Math.floor((float) a / (float) b);
                var vm = new LuaVM();
                vm.load("return %s//%s".formatted((float) a, (float) b));
                var res = vm.run();
                Assertions.assertEquals(LuaVM.VmRunState.SUCCESS, res.state());
                var rvs = res.returnVars();
                Assertions.assertEquals(1, rvs.length);
                var rv = (float) rvs[0];
                Assertions.assertEquals(expected, rv, 0.000001f);
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
                    print("1")
                    local b <close> = a
                    print("2")
                    local c <close> = a
                    print("3")
                end
                                
                return rv""", new Object[]{"closing;closing;"});

        loadAssertSuccessAndRv("""
                rv = ""
                do
                    local a1 =  {__close = function() rv=rv.."closinga1"..";" end}
                    setmetatable(a1,a1)
                    local a2 =  {__close = function() rv=rv.."closinga2"..";" end}
                    setmetatable(a2,a2)
                    print("1")
                    local b <close> = a1
                    print("2")
                    local c <close> = a2
                    print("3")
                end
                return rv""", new Object[]{"closinga2;closinga1;"});

        loadAssertSuccessAndRv("""
                origPrint = print
                                
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
                                
                origPrint(rv)
                return rv
                """, new Object[]{"iter;b;fvala table;closing;iter;b;fvala table;closing;iter;b;fvala table;closing;c;done;"});

        loadAssertException("""
                §local|§ mt = {["__close"]=function() end}
                setmetatable(mt,mt)
                a <§close|const§> = mt
                """, LuaParserException.class);

        loadAssertSuccessAndRv("""
                §local|§ mt = {["__close"]=function() end}
                setmetatable(mt,mt)
                local a <§close|const§> = mt
                return "ok
                """, new Object[]{"ok"});
    }

    @Test
    void globals() {
        loadAssertSuccessAndRv("""
                do a = 1 end
                function b() a = 2 end
                b()
                return a
                """, new Object[]{2});
    }
}
