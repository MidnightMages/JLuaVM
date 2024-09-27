package dev.asdf00.jluavm.parser;

import dev.asdf00.jluavm.parsing.Parser;
import dev.asdf00.jluavm.parsing.exceptions.LuaParserException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;

import static dev.asdf00.jluavm.Constants.largeValidLuaProgram;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SnippetTest {

    private Parser parse(String code) {
        var parser = new Parser(code);
        parser.parse();
        return parser;
    }

    @Test
    void simple() {
        var parser = parse("""
                for i = 1,5 do
                    print(i+5)
                end""");
    }

    @Test
    void binUnOps() {
        var parser = parse("""
                local a = 1
                local b = 2
                """);
    }

    @Test
    void singleBlock() {
        var parser = parse("""
                local i = 0
                do print(i+5) end
                do print(i+i) end
                do print(5+i) end
                do print(5) end
                """);
    }


    @Test
    void testBlock() {
        var parser = parse("""
                return subscribedCoroutineMap[a]
                """);

        parser = parser;
    }

    @Test
    void testBlock2() {
        var parser = parse("""
                subscribedCoroutineMap[eventKey] = 1
                return 1
                """);

        parser = parser;
    }

    @Test
    void testAssignNonAssignable() {
        assertThrows(LuaParserException.class, () -> parse("""
                subscribedCoroutineMap[eventKey]() = 1
                return 1
                """));
    }

    @Test
    void testStatementEndsOnAssignable() {
        assertThrows(LuaParserException.class, () -> parse("""
                subscribedCoroutineMap[eventKey]
                return 1
                """));
    }

    @Test
    void complexBlock() {
        var parser = parse(largeValidLuaProgram);
    }

    @Test
    void invalidBlocks() {
        var snippets = new String[]{
                "do while true do end",
                "(do end)",
                "do (end)",
                "do (a=1) end",
                "do (1) end",
                "do (1,2) end",
                "while while true do end end",
                "while (while true do end) do end",
                "end", "do",
                "true", "false", "1", "not 1",
                "§local| §a =; 1§|==b§",
                "break§| §;",
                "a,a,§\"a\"|'a'|;||2|a[1[]]§ = 1,2,3",
                "=1", "local local i = 1",
                "local §and|or|not|function§ = §123|'aaa'|\"aaaaa\"|a|§",
                "(§a||a+1§)[1]",
                "::§function|1aaa,end§::", "::", "goto §not|do||\n§",
                "if true then return 1 else return 2 else return 3 end"
        };

        var workingSnippets = new String[]{
                "goto\ntest"
        };

        snippets = expandOptions(snippets);
        for (var s : snippets)
            assertThrows(LuaParserException.class, () -> parse(s), "Testcode: " + s);

        workingSnippets = expandOptions(workingSnippets);
        for (var s : workingSnippets)
            assertDoesNotThrow(() -> parse(s), "Testcode: " + s);
    }

    private static String[] expandOptions(String[] snippets) {
        return Arrays.stream(snippets).flatMap(s -> {
            if (!s.contains("§"))
                return Stream.of(s);

            var expanded = new ArrayList<String>();
            expanded.add(s);
            boolean anyExpanded;
            do {
                anyExpanded = false;
                for (int i = 0; i < expanded.size(); i++) {
                    var curr = expanded.get(i);
                    var firstGroupStartIndex = curr.indexOf("§");
                    if (firstGroupStartIndex == -1)
                        continue;

                    anyExpanded = true;
                    var firstGroupEndIndex = curr.indexOf("§", firstGroupStartIndex + 1);
                    var firstGroup = curr.substring(firstGroupStartIndex + 1, firstGroupEndIndex);
                    var replacements = firstGroup.split("\\|", -1);
                    boolean isFirst = true;
                    for (var rep : replacements) {
                        var repString = curr.substring(0, firstGroupStartIndex) + rep + curr.substring(firstGroupEndIndex + 1);
                        if (isFirst) {
                            isFirst = false;
                            expanded.set(i, repString);
                        } else {
                            expanded.add(repString);
                        }
                    }
                }
            } while (anyExpanded);
            return expanded.stream();
        }).toArray(String[]::new);
    }
}
