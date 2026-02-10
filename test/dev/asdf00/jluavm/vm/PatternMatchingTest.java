package dev.asdf00.jluavm.vm;

import dev.asdf00.jluavm.runtime.types.LuaObject;
import org.junit.jupiter.api.Test;

public class PatternMatchingTest extends BaseVmTest {
    @Test
    void gmatchIndexExtraction() {
        loadAssertSuccessAndRv("""
                rv = ""
                for v in string.gmatch("Some example EEEeeeeE", "()e") do
                    rv = rv .. tostring(v) .. ";"
                end
                return rv
                """, LuaObject.of("4;6;12;17;18;19;20;"));
    }

    @Test
    void broadGsub() {
        loadAssertSuccessAndRv("""
                local rv = ""
                local text = "Blabla Bee bee bumblebee banana.%123af"
                local premadePattern = "bee"
                local patternOptions = {"a", "b", "c", "d", "e", "f", "g", ".", "%a", "%c", "%d", "%g",\s
                  "%l", "%p", "%s", "%u", "%w", "%x", "%%", "%.", " ", "[ ]", " [abc]",\s
                  " [^abc]", " [%l]", " [^%l]", "%A", "%C", "%D", "%G", "%L", "%P", "%S",\s
                  "%U", "%W", "%X", "()"}

                local function appendResult(...)
                  local tbl = {...}
                  local sres = ""
                  for _,v in ipairs(tbl) do sres = sres .. tostring(v) .. "|" end
                  rv = rv .. sres:sub(1,-2) .. "\\n"
                end

                for _,p in ipairs(patternOptions) do
                  appendResult(string.gsub(text, premadePattern..p, "#"))
                  appendResult(string.gsub(text, p..premadePattern, "#"))
                  appendResult(string.gsub(text, p, "#"))
                  appendResult(string.gsub(text, "("..p..")", "%1"))
                end
                appendResult(string.gsub(text, "(%w*)" , "%1 %1"))
                appendResult(string.gsub(text, "(%w+)" , "%1 %1"))
                appendResult(string.gsub(text, "(%w-)" , "%1 %1"))
                appendResult(string.gsub(text, "(%w?)" , "%1 %1"))
                appendResult(string.gsub(text, "(%w?)%1" , "%1 %1"))
                appendResult(string.gsub(text, "(%beb)" , ">>%1<<"))
                appendResult(string.gsub(text, "(%f[abc])" , ">%1<"))
                appendResult(string.gsub(text, "^bee" , ">%1<"))
                appendResult(string.gsub(text, "bee$" , ">%1<"))
                appendResult(string.gsub(text, "^Bla" , ">%1<"))
                appendResult(string.gsub(text, "af$" , ">%1<"))
                appendResult(string.gsub("abc", "()a*()", "%1 %2"))

                return rv
                """, LuaObject.of("""
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Bl#bl# Bee bee bumblebee b#n#n#.%123#f|6
                Blabla Bee bee bumblebee banana.%123af|6
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Bla#la Bee #ee #um#le#ee #anana.%123af|6
                Blabla Bee bee bumblebee banana.%123af|6
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumbl# banana.%123af|1
                Blabla B## b## bumbl#b## banana.%123af|7
                Blabla Bee bee bumblebee banana.%123af|7
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123a#|1
                Blabla Bee bee bumblebee banana.%123af|1
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee #bumble#banana.%123af|2
                Blabla Bee# bumbl# banana.%123af|2
                ######################################|38
                Blabla Bee bee bumblebee banana.%123af|38
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumbl# banana.%123af|1
                ###### ### ### ######### ######.%123##|29
                Blabla Bee bee bumblebee banana.%123af|29
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%###af|3
                Blabla Bee bee bumblebee banana.%123af|3
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumbl# banana.%123af|1
                ###### ### ### ######### #############|34
                Blabla Bee bee bumblebee banana.%123af|34
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumbl# banana.%123af|1
                B##### B## ### ######### ######.%123##|27
                Blabla Bee bee bumblebee banana.%123af|27
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana##123af|2
                Blabla Bee bee bumblebee banana.%123af|2
                Blabla Bee #bumble#banana.%123af|2
                Blabla Bee# bumblebee banana.%123af|1
                Blabla#Bee#bee#bumblebee#banana.%123af|4
                Blabla Bee bee bumblebee banana.%123af|4
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                #labla #ee bee bumblebee banana.%123af|2
                Blabla Bee bee bumblebee banana.%123af|2
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumbl# banana.%123af|1
                ###### ### ### ######### ######.%#####|32
                Blabla Bee bee bumblebee banana.%123af|32
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumbl# banana.%123af|1
                #l##l# ### ### #um#l#### ##n#n#.%#####|25
                Blabla Bee bee bumblebee banana.%123af|25
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.#123af|1
                Blabla Bee bee bumblebee banana.%123af|1
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana#%123af|1
                Blabla Bee bee bumblebee banana.%123af|1
                Blabla Bee #bumble#banana.%123af|2
                Blabla Bee# bumblebee banana.%123af|1
                Blabla#Bee#bee#bumblebee#banana.%123af|4
                Blabla Bee bee bumblebee banana.%123af|4
                Blabla Bee #bumble#banana.%123af|2
                Blabla Bee# bumblebee banana.%123af|1
                Blabla#Bee#bee#bumblebee#banana.%123af|4
                Blabla Bee bee bumblebee banana.%123af|4
                Blabla Bee #umble#anana.%123af|2
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee#ee#umblebee#anana.%123af|3
                Blabla Bee bee bumblebee banana.%123af|3
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla#ee bee bumblebee banana.%123af|1
                Blabla Bee bee bumblebee banana.%123af|1
                Blabla Bee #umble#anana.%123af|2
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee#ee#umblebee#anana.%123af|3
                Blabla Bee bee bumblebee banana.%123af|3
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla#ee bee bumblebee banana.%123af|1
                Blabla Bee bee bumblebee banana.%123af|1
                Blabla Bee #bumble#banana.%123af|2
                Blabla Bee# bumblebee banana.%123af|1
                Blabla#Bee#bee#bumblebee#banana#####af|9
                Blabla Bee bee bumblebee banana.%123af|9
                Blabla Bee #bumble#banana.%123af|2
                Blabla Bee# bumbl# banana.%123af|2
                ######################################|38
                Blabla Bee bee bumblebee banana.%123af|38
                Blabla Bee #bumble#banana.%123af|2
                Blabla Bee# bumbl# banana.%123af|2
                #################################123##|35
                Blabla Bee bee bumblebee banana.%123af|35
                Blabla Bee #bumble#banana.%123af|2
                Blabla Bee# bumblebee banana.%123af|1
                Blabla#Bee#bee#bumblebee#banana.%123af|4
                Blabla Bee bee bumblebee banana.%123af|4
                Blabla Bee #bumble#banana.%123af|2
                Blabla Bee# bumblebee banana.%123af|1
                #labla##ee#bee#bumblebee#banana#####af|11
                Blabla Bee bee bumblebee banana.%123af|11
                Blabla Bee #bumble#banana.%123af|2
                Blabla Bee# bumbl# banana.%123af|2
                ###############################.%#####|36
                Blabla Bee bee bumblebee banana.%123af|36
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumbl# banana.%123af|1
                ###### ### ### ######### #############|34
                Blabla Bee bee bumblebee banana.%123af|34
                Blabla Bee #bumble#banana.%123af|2
                Blabla Bee# bumbl# banana.%123af|2
                B######B##############################|36
                Blabla Bee bee bumblebee banana.%123af|36
                Blabla Bee #bumble#banana.%123af|2
                Blabla Bee# bumblebee banana.%123af|1
                Blabla#Bee#bee#bumblebee#banana##123af|6
                Blabla Bee bee bumblebee banana.%123af|6
                Blabla Bee #bumble#banana.%123af|2
                Blabla Bee# bumblebee banana.%123af|1
                B#ab#a#Bee#bee#b##b#ebee#ba#a#a##123af|13
                Blabla Bee bee bumblebee banana.%123af|13
                Blabla Bee # bumble# banana.%123af|2
                Blabla Bee # bumble# banana.%123af|2
                #B#l#a#b#l#a# #B#e#e# #b#e#e# #b#u#m#b#l#e#b#e#e# #b#a#n#a#n#a#.#%#1#2#3#a#f#|39
                Blabla Bee bee bumblebee banana.%123af|39
                Blabla Blabla Bee Bee bee bee bumblebee bumblebee banana banana. %123af 123af|7
                Blabla Blabla Bee Bee bee bee bumblebee bumblebee banana banana.%123af 123af|6
                 B l a b l a   B e e   b e e   b u m b l e b e e   b a n a n a . % 1 2 3 a f |39
                B Bl la ab bl la a B Be ee e b be ee e b bu um mb bl le eb be ee e b ba an na an na a. %1 12 23 3a af f|33
                 B l a b l a   Be e  be e  b u m b l e be e  b a n a n a . % 1 2 3 a f |33
                Blabla Be>>e b<<>>ee bumb<<l>>eb<<e>>e b<<anana.%123af|4
                Bl><abl><a Bee ><bee ><bum><ble><bee ><ban><an><a.%123><af|10
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                >Bla<bla Bee bee bumblebee banana.%123af|1
                Blabla Bee bee bumblebee banana.%123>af<|1
                1 2b3 3c4 4|3
                """));
    }
}
