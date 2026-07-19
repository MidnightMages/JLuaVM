package dev.asdf00.jluavm.vm;

import dev.asdf00.jluavm.runtime.types.LuaObject;
import org.junit.jupiter.api.Test;

/**
 * Contains tests that are purely about user-made applications of this runtime
 */
@SuppressWarnings("unused")
public class UserSnippetTest extends BaseVmTest {

    /**
     * Decompresses a string via :match, :gsub and a replacement function
     */
    @Test
    void gsubWithReplacementFunc() {
        loadAssertSuccessAndRv("""
                load = function(x)
                    return function() return x end
                end
                
                                
                output =
                load(([[3a=components;3b=8gpuG3c=b:newU110,44)b:assignUc,8screenG)b=8computerG3V=0,0;3f=tableJNYV=c:pasteText(V,"SCROLL_SPILL_CLEAR",7)9JXYN(7.."\\nG9;3g=N;3h=X;h("Lua shell:GK>>> G3iQ"3j=faD;3W;while""do 3l,m=b:getMachineEvent()qHl 6sleep(0.05)k=k-0.05;qk<0 6g(j and"\\b"or"_Gj=Hj;W.5 9 eDql=Qshutdown"6break eDql=QkeyPressed"6qm~Q"6qj 6j=faD;K\\bGW 9;qm=Q\\b"6if#i>0 6K\\bGi=i:sub(1,-2)9 eDqm=Q\\n"6K\\nG3n,o=load(i)qHn 6zeD n,o=xpcall(n,debug.traceback)qHn 6z9 9;K>>> GiQ"eD g(m)i=i..m 9 9 9 9]]):gsub("%w",function(y)return([[@3local @6then @7f.concat(f.pack(...)," ")@8a:getFirst("@9end@qif @zh("Error: ",o)@Dlse@G")@Hnot @J;function @Kg("@Nwrite@Q="@UBuffer(@Vd,e@Wk=0@Xprint@Y(...)@]]):match("@"..y.."(.-)@")end))()
                                
                expected = [[local a=components;local b=a:getFirst("gpu")local c=b:newBuffer(110,44)b:assignBuffer(c,a:getFirst("screen"))b=a:getFirst("computer")local d,e=0,0;local f=table;function write(...)d,e=c:pasteText(d,e,"SCROLL_SPILL_CLEAR",f.concat(f.pack(...)," "))end;function print(...)write(f.concat(f.pack(...)," ").."\\n")end;local g=write;local h=print;h("Lua shell:")g(">>> ")local i=""local j=false;local k=0;while""do local l,m=b:getMachineEvent()if not l then sleep(0.05)k=k-0.05;if k<0 then g(j and"\\b"or"_")j=not j;k=0.5 end elseif l=="shutdown"then break elseif l=="keyPressed"then if m~=""then if j then j=false;g("\\b")k=0 end;if m=="\\b"then if#i>0 then g("\\b")i=i:sub(1,-2)end elseif m=="\\n"then g("\\n")local n,o=load(i)if not n then h("Error: ",o)else n,o=xpcall(n,debug.traceback)if not n then h("Error: ",o)end end;g(">>> ")i=""else g(m)i=i..m end end end end]]
                                
                if output == expected then
                    return "ok"
                else
                    return output
                end
                """, LuaObject.of("ok"));
    }
}
