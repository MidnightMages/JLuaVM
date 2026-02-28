package dev.asdf00.jluavm.vm;

import dev.asdf00.jluavm.runtime.types.LuaObject;
import org.junit.jupiter.api.Test;

public class PatternMatchingTest extends BaseVmTest {
    private static final String globalImpls = """
                string.gmatch = function(s, pattern, initPos)
                    local startPos = initPos
                    while true do
                         string.match(s, pattern, startPos)
                    end
                end
            """;

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
                    local patternOptions = {"a", "b", "c", "d", "e", "f", "g", ".", "%a", "%c", "%d", "%g",
                      "%l", "%p", "%s", "%u", "%w", "%x", "%%", "%.", " ", "[ ]", " [abc]",
                      " [^abc]", " [%l]", " [^%l]", "%A", "%C", "%D", "%G", "%L", "%P", "%S",
                      "%U", "%W", "%X" --[[, "()"]]}

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
                --  appendResult(string.gsub(text, "(%w*)" , "%1 %1"))
                    appendResult(string.gsub(text, "(%w+)" , "%1 %1"))
                --  appendResult(string.gsub(text, "(%w-)" , "%1 %1"))
                    --appendResult(string.gsub(text, "(%w?)" , "%1 %1"))
                --  appendResult(string.gsub(text, "(%w?)%1" , "%1 %1"))
                    --appendResult(string.gsub(text, "(%beb)" , ">>%1<<"))
                --  appendResult(string.gsub(text, "(%f[abc])" , ">%1<"))
                    appendResult(string.gsub(text, "^bee" , ">%1<"))
                    appendResult(string.gsub(text, "bee$" , ">%1<"))
                    appendResult(string.gsub(text, "^Bla" , ">%1<"))
                    appendResult(string.gsub(text, "af$" , ">%1<"))
                    --appendResult(string.gsub("abc", "()a*()", "%1 %2"))
                                
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
                Blabla Blabla Bee Bee bee bee bumblebee bumblebee banana banana.%123af 123af|6
                Blabla Bee bee bumblebee banana.%123af|0
                Blabla Bee bee bumblebee banana.%123af|0
                >Bla<bla Bee bee bumblebee banana.%123af|1
                Blabla Bee bee bumblebee banana.%123>af<|1
                """));
    }

    @Test
    void broadGmatch() {
        loadAssertSuccessAndRv("""
                local rv = "_"
                local text = "Blabla Bee bee bumblebee banana.%123af"
                local premadePattern = "bee"
                local patternOptions = {"a", "b", "c", "d", "e", "f", "g", ".", "%a", "%c", "%d", "%g",
                  "%l", "%p", "%s", "%u", "%w", "%x", "%%", "%.", " ", "[ ]", " [abc]",
                  " [^abc]", " [%l]", " [^%l]", "%A", "%C", "%D", "%G", "%L", "%P", "%S",
                  "%U", "%W", "%X"--[[, "()"]]}
                
                local function appendResult(it)
                  local tbl = {}
                  for match in it do table.insert(tbl, match) end
                  local sres = ""
                  for _,v in ipairs(tbl) do sres = sres .. tostring(v) .. "|" end
                  rv = rv .. "." .. sres:sub(1,-2) .. "\\n"
                end
                
                for _,p in ipairs(patternOptions) do
                  appendResult(string.gmatch(text, premadePattern..p))
                  appendResult(string.gmatch(text, p..premadePattern))
                  appendResult(string.gmatch(text, p))
                  appendResult(string.gmatch(text, "("..p..")"))
                  rv=rv..p.."========\\n"
                end
                appendResult(string.gmatch(text, "(%w*)"))
                appendResult(string.gmatch(text, "(%w+)"))
                appendResult(string.gmatch(text, "(%w-)"))
                --appendResult(string.gmatch(text, "(%w?)"))
                appendResult(string.gmatch(text, "(%w?)%1"))
                --appendResult(string.gmatch(text, "(%beb)"))
                appendResult(string.gmatch(text, "(%f[abc])"))
                appendResult(string.gmatch(text, "^bee"))
                appendResult(string.gmatch(text, "bee$"))
                appendResult(string.gmatch(text, "^Bla"))
                appendResult(string.gmatch(text, "af$"))
                return rv
                """, LuaObject.of("""
                _.
                .
                .a|a|a|a|a|a
                .a|a|a|a|a|a
                a========
                .
                .
                .b|b|b|b|b|b
                .b|b|b|b|b|b
                b========
                .
                .
                .
                .
                c========
                .
                .
                .
                .
                d========
                .
                .ebee
                .e|e|e|e|e|e|e
                .e|e|e|e|e|e|e
                e========
                .
                .
                .f
                .f
                f========
                .
                .
                .
                .
                g========
                .bee |bee\s
                . bee|ebee
                .B|l|a|b|l|a| |B|e|e| |b|e|e| |b|u|m|b|l|e|b|e|e| |b|a|n|a|n|a|.|%|1|2|3|a|f
                .B|l|a|b|l|a| |B|e|e| |b|e|e| |b|u|m|b|l|e|b|e|e| |b|a|n|a|n|a|.|%|1|2|3|a|f
                .========
                .
                .ebee
                .B|l|a|b|l|a|B|e|e|b|e|e|b|u|m|b|l|e|b|e|e|b|a|n|a|n|a|a|f
                .B|l|a|b|l|a|B|e|e|b|e|e|b|u|m|b|l|e|b|e|e|b|a|n|a|n|a|a|f
                %a========
                .
                .
                .
                .
                %c========
                .
                .
                .1|2|3
                .1|2|3
                %d========
                .
                .ebee
                .B|l|a|b|l|a|B|e|e|b|e|e|b|u|m|b|l|e|b|e|e|b|a|n|a|n|a|.|%|1|2|3|a|f
                .B|l|a|b|l|a|B|e|e|b|e|e|b|u|m|b|l|e|b|e|e|b|a|n|a|n|a|.|%|1|2|3|a|f
                %g========
                .
                .ebee
                .l|a|b|l|a|e|e|b|e|e|b|u|m|b|l|e|b|e|e|b|a|n|a|n|a|a|f
                .l|a|b|l|a|e|e|b|e|e|b|u|m|b|l|e|b|e|e|b|a|n|a|n|a|a|f
                %l========
                .
                .
                ..|%
                ..|%
                %p========
                .bee |bee\s
                . bee
                . | | |\s
                . | | |\s
                %s========
                .
                .
                .B|B
                .B|B
                %u========
                .
                .ebee
                .B|l|a|b|l|a|B|e|e|b|e|e|b|u|m|b|l|e|b|e|e|b|a|n|a|n|a|1|2|3|a|f
                .B|l|a|b|l|a|B|e|e|b|e|e|b|u|m|b|l|e|b|e|e|b|a|n|a|n|a|1|2|3|a|f
                %w========
                .
                .ebee
                .B|a|b|a|B|e|e|b|e|e|b|b|e|b|e|e|b|a|a|a|1|2|3|a|f
                .B|a|b|a|B|e|e|b|e|e|b|b|e|b|e|e|b|a|a|a|1|2|3|a|f
                %x========
                .
                .
                .%
                .%
                %%========
                .
                .
                ..
                ..
                %.========
                .bee |bee\s
                . bee
                . | | |\s
                . | | |\s
                 ========
                .bee |bee\s
                . bee
                . | | |\s
                . | | |\s
                [ ]========
                .bee b|bee b
                .
                . b| b| b
                . b| b| b
                 [abc]========
                .
                .
                . B
                . B
                 [^abc]========
                .bee b|bee b
                .
                . b| b| b
                . b| b| b
                 [%l]========
                .
                .
                . B
                . B
                 [^%l]========
                .bee |bee\s
                . bee
                . | | | |.|%|1|2|3
                . | | | |.|%|1|2|3
                %A========
                .bee |bee\s
                . bee|ebee
                .B|l|a|b|l|a| |B|e|e| |b|e|e| |b|u|m|b|l|e|b|e|e| |b|a|n|a|n|a|.|%|1|2|3|a|f
                .B|l|a|b|l|a| |B|e|e| |b|e|e| |b|u|m|b|l|e|b|e|e| |b|a|n|a|n|a|.|%|1|2|3|a|f
                %C========
                .bee |bee\s
                . bee|ebee
                .B|l|a|b|l|a| |B|e|e| |b|e|e| |b|u|m|b|l|e|b|e|e| |b|a|n|a|n|a|.|%|a|f
                .B|l|a|b|l|a| |B|e|e| |b|e|e| |b|u|m|b|l|e|b|e|e| |b|a|n|a|n|a|.|%|a|f
                %D========
                .bee |bee\s
                . bee
                . | | |\s
                . | | |\s
                %G========
                .bee |bee\s
                . bee
                .B| |B| | | |.|%|1|2|3
                .B| |B| | | |.|%|1|2|3
                %L========
                .bee |bee\s
                . bee|ebee
                .B|l|a|b|l|a| |B|e|e| |b|e|e| |b|u|m|b|l|e|b|e|e| |b|a|n|a|n|a|1|2|3|a|f
                .B|l|a|b|l|a| |B|e|e| |b|e|e| |b|u|m|b|l|e|b|e|e| |b|a|n|a|n|a|1|2|3|a|f
                %P========
                .
                .ebee
                .B|l|a|b|l|a|B|e|e|b|e|e|b|u|m|b|l|e|b|e|e|b|a|n|a|n|a|.|%|1|2|3|a|f
                .B|l|a|b|l|a|B|e|e|b|e|e|b|u|m|b|l|e|b|e|e|b|a|n|a|n|a|.|%|1|2|3|a|f
                %S========
                .bee |bee\s
                . bee|ebee
                .l|a|b|l|a| |e|e| |b|e|e| |b|u|m|b|l|e|b|e|e| |b|a|n|a|n|a|.|%|1|2|3|a|f
                .l|a|b|l|a| |e|e| |b|e|e| |b|u|m|b|l|e|b|e|e| |b|a|n|a|n|a|.|%|1|2|3|a|f
                %U========
                .bee |bee\s
                . bee
                . | | | |.|%
                . | | | |.|%
                %W========
                .bee |bee\s
                . bee
                .l|l| | | |u|m|l| |n|n|.|%
                .l|l| | | |u|m|l| |n|n|.|%
                %X========
                .Blabla|Bee|bee|bumblebee|banana||123af
                .Blabla|Bee|bee|bumblebee|banana|123af
                .||||||||||||||||||||||||||||||||||||||
                .||||||||e||e||||||||e||||||||||||||
                .|||||||||
                .
                .
                .
                .af
                """));
    }

    @Test
    void broadMatch() {
        loadAssertSuccessAndRv("""
                local rv = "_"
                local text = "Blabla Bee bee bumblebee banana.%123af"
                local premadePattern = "bee"
                local patternOptions = {"a", "b", "c", "d", "e", "f", "g", ".", "%a", "%c", "%d", "%g",
                  "%l", "%p", "%s", "%u", "%w", "%x", "%%", "%.", " ", "[ ]", " [abc]",
                  " [^abc]", " [%l]", " [^%l]", "%A", "%C", "%D", "%G", "%L", "%P", "%S",
                  "%U", "%W", "%X"--[[, "()"]]}

                local function appendResult(...)
                  local tbl = {...}
                  local sres = ""
                  for _,v in ipairs(tbl) do sres = sres .. tostring(v) .. "|" end
                  rv = rv ..".".. sres:sub(1,-2) .. "\\n"
                end

                for _,p in ipairs(patternOptions) do
                  appendResult(string.match(text, premadePattern..p))
                  appendResult(string.match(text, p..premadePattern))
                  appendResult(string.match(text, p))
                  appendResult(string.match(text, "("..p..")"))
                end
                appendResult(string.match(text, "(%w*)"))
                appendResult(string.match(text, "(%w+)"))
                appendResult(string.match(text, "(%w-)"))
                appendResult(string.match(text, "(%w?)"))
                appendResult(string.match(text, "(%w?)%1"))
                --appendResult(string.match(text, "(%beb)"))
                appendResult(string.match(text, "(%f[abc])"))
                appendResult(string.match(text, "^bee"))
                appendResult(string.match(text, "bee$"))
                appendResult(string.match(text, "^Bla"))
                appendResult(string.match(text, "af$"))
                --appendResult(string.match("abc", "()a*()"))
                                
                return rv
                """, LuaObject.of("""
                _.
                .
                .a
                .a
                .
                .
                .b
                .b
                .
                .
                .
                .
                .
                .
                .
                .
                .
                .ebee
                .e
                .e
                .
                .
                .f
                .f
                .
                .
                .
                .
                .bee\s
                . bee
                .B
                .B
                .
                .ebee
                .B
                .B
                .
                .
                .
                .
                .
                .
                .1
                .1
                .
                .ebee
                .B
                .B
                .
                .ebee
                .l
                .l
                .
                .
                ..
                ..
                .bee\s
                . bee
                .\s
                .\s
                .
                .
                .B
                .B
                .
                .ebee
                .B
                .B
                .
                .ebee
                .B
                .B
                .
                .
                .%
                .%
                .
                .
                ..
                ..
                .bee\s
                . bee
                .\s
                .\s
                .bee\s
                . bee
                .\s
                .\s
                .bee b
                .
                . b
                . b
                .
                .
                . B
                . B
                .bee b
                .
                . b
                . b
                .
                .
                . B
                . B
                .bee\s
                . bee
                .\s
                .\s
                .bee\s
                . bee
                .B
                .B
                .bee\s
                . bee
                .B
                .B
                .bee\s
                . bee
                .\s
                .\s
                .bee\s
                . bee
                .B
                .B
                .bee\s
                . bee
                .B
                .B
                .
                .ebee
                .B
                .B
                .bee\s
                . bee
                .l
                .l
                .bee\s
                . bee
                .\s
                .\s
                .bee\s
                . bee
                .l
                .l
                .Blabla
                .Blabla
                .
                .B
                .
                .
                .
                .
                .Bla
                .af
                """));
    }

    @Test
    void simpleMatchCapture() {
        loadAssertSuccessAndRv("""
                local text = "Blabla Bee1 bee2 bumblebee banana.%123af"
                return string.match(text, "b(ee..)")
                """, LuaObject.of("ee2 "));
    }

    @Test
    void simpleDoubleMatchCapture() {
        loadAssertSuccessAndRv("""
                local text = "Blabla Bee1 bee2 bumblebee banana.%123af"
                return string.match(text, "b(ee..)(.)")
                """, new LuaObject[]{LuaObject.of("ee2 "), LuaObject.of("b")});
    }

    @Test
    void singleGmatch() {
        loadAssertSuccessAndRv("""
                local rv = "_"
                local text = "Blabla Bee bee bumblebee banana.%123af"
                
                local function appendResult(it)
                  local tbl = {}
                  for match in it do table.insert(tbl, match) end
                  local sres = ""
                  for _,v in ipairs(tbl) do sres = sres .. tostring(v) .. "|" end
                  rv = rv .. "." .. sres:sub(1,-2) .. "\\n"
                end
                
                appendResult(string.gmatch(text, "a"))
                return rv
                """, new LuaObject[]{LuaObject.of("_.a|a|a|a|a|a\n")});
    }

    @Test
    void singleGmatch2() {
        loadAssertSuccessAndRv("""
                local rv = "_"
                local text = "Blabla Bee bee bumblebee banana.%123af"
                               \s
                local function appendResult(it)
                  local tbl = {}
                  for match in it do table.insert(tbl, match) end
                  local sres = ""
                  for _,v in ipairs(tbl) do sres = sres .. tostring(v) .. "|" end
                  rv = rv .. "." .. sres:sub(1,-2) .. "\\n"
                end
                
                appendResult(string.gmatch(text, " [^abc]"))
                return rv
                """, new LuaObject[]{LuaObject.of("_. B\n")});
    }

    @Test
    void singleGsub() {
        loadAssertSuccessAndRv("""
                local rv = "_"
                local text = "Blabla Bee bee bumblebee banana.%123af"
                
                local function appendResult(...)
                  local tbl = {...}
                  local sres = ""
                  for _,v in ipairs(tbl) do sres = sres .. tostring(v) .. "|" end
                  rv = rv .. "." .. sres:sub(1,-2) .. "\\n"
                end
                
                appendResult(string.gsub(text, "(%w*)" , "%1 %1"))
                return rv
                """, new LuaObject[]{LuaObject.of("_.Blabla Blabla Bee Bee bee bee bumblebee bumblebee banana banana. %123af 123af|7\n")});
    }


    @Test
    void broadGsub_failingBits() {
        loadAssertSuccessAndRv("""
                local rv = ""
                local text = "Blabla Bee bee bumblebee banana.%123af"
                local premadePattern = "bee"
                local patternOptions = {"a", "b", "c", "d", "e", "f", "g", ".", "%a", "%c", "%d", "%g",
                  "%l", "%p", "%s", "%u", "%w", "%x", "%%", "%.", " ", "[ ]", " [abc]",
                  " [^abc]", " [%l]", " [^%l]", "%A", "%C", "%D", "%G", "%L", "%P", "%S",
                  "%U", "%W", "%X" --[[, "()"]]}

                local function appendResult(...)
                  local tbl = {...}
                  local sres = ""
                  for _,v in ipairs(tbl) do sres = sres .. tostring(v) .. "|" end
                  rv = rv .. sres:sub(1,-2) .. "\\n"
                end

                appendResult(string.gsub(text, "(%w*)" , "%1 %1"))
                appendResult(string.gsub(text, "(%w-)" , "%1 %1"))
                appendResult(string.gsub(text, "(%w?)%1" , "%1 %1"))
                appendResult(string.gsub(text, "(%f[abc])" , ">%1<"))
                
                return rv
                """, LuaObject.of("""
                Blabla Blabla Bee Bee bee bee bumblebee bumblebee banana banana. %123af 123af|7
                 B l a b l a   B e e   b e e   b u m b l e b e e   b a n a n a . % 1 2 3 a f |39
                 B l a b l a   Be e  be e  b u m b l e be e  b a n a n a . % 1 2 3 a f |33
                Bl><abl><a Bee ><bee ><bum><ble><bee ><ban><an><a.%123><af|10
                """));
    }

}
