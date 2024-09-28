package dev.asdf00.jluavm.vm;

import dev.asdf00.jluavm.LuaVM;
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
}
