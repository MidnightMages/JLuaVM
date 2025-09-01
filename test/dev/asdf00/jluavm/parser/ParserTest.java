package dev.asdf00.jluavm.parser;

import dev.asdf00.jluavm.exceptions.loading.LuaSemanticException;
import dev.asdf00.jluavm.parsing.Parser;
import dev.asdf00.jluavm.parsing.SymTable;
import dev.asdf00.jluavm.exceptions.loading.LuaLexerException;
import dev.asdf00.jluavm.exceptions.loading.LuaParserException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static dev.asdf00.jluavm.Constants.largeValidLuaProgram;
import static dev.asdf00.jluavm.Util.expandOptions;
import static org.junit.jupiter.api.Assertions.*;

public class ParserTest {

    static Parser parse(String code) {
        var parser = new Parser(code);
        parser.parse();
        return parser;
    }

    private String getSymTab(Parser parser) {
        try {
            var f = Parser.class.getDeclaredField("symTab");
            f.setAccessible(true);
            var st = (SymTable) f.get(parser);
            f = SymTable.class.getDeclaredField("prevScope");
            f.setAccessible(true);
            var rscope = f.get(st);
            var m = rscope.getClass().getDeclaredMethod("toFullString");
            m.setAccessible(true);
            return (String) m.invoke(rscope);
        }
        catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
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
                i = 3
                """);
    }


    @Test
    void testBlock() {
        var parser = parse("""
                return subscribedCoroutineMap[a]
                """);
    }

    @Test
    void testBlock2() {
        var src = """
                §;|§subscribedCoroutineMap[eventKey] = 1§;do end|§
                §;|§return 1§;||;;|;;;§
                """;
        for (var expanded : expandOptions(src)) {
            if (expanded.trim().endsWith(";;"))
                assertThrows(LuaParserException.class, () -> parse(expanded), "Code was: " + expanded);
            else
                assertDoesNotThrow(() -> parse(expanded), "Code was: " + expanded);
        }
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
                "a,a,§\"a\"|'a'|;||2|a[1[]]§ = 1,2,3",
                "=1", "local local i = 1",
                "local §and|or|not|function§ = §123|'aaa'|\"aaaaa\"|a|§",
                "(§a||a+1§)[1]",
                "::§function|baaa,end§::", "::", "goto §not|do||\n§",
                "if true then return 1 else return 2 else return 3 end",
                "::1234::"
        };

        snippets = expandOptions(snippets);
        for (var s : snippets)
            assertThrows(LuaParserException.class, () -> parse(s), "Testcode: " + s);

        var semanticSnippets = new String[]{
                "goto\ntest"
        };
        for (var s : semanticSnippets)
            assertThrows(LuaSemanticException.class,() -> parse(s), "Testcode: " + s);

        var lexerSnippets = new String[]{
                "::1aaa,end::"
        };
        for (var s : lexerSnippets)
            assertThrows(LuaLexerException.class,() -> parse(s), "Testcode: " + s);
    }

    @Test
    void simpleLoops() {
        var src = new String[]{
                "repeat until true; ",
                "while true do §return false|§ end",
                "repeat §return false|§ until true§;|§",
                "for §k,|§v in pairs({}) do §return false||bla('a')§ end",
                "for i=1,4§|,2§ do §return false||bla('a')§ end",
        };
        for (var expanded : expandOptions(src))
            assertDoesNotThrow(() -> parse(expanded), () -> "Code: " + expanded);
    }

    void assertThrowsLexerOrParserException(Runnable r, Supplier<String> err) {
        try {
            r.run();
        }
        catch (LuaLexerException | LuaParserException ignored) {
        }
        catch (Exception ex) {
            Assertions.fail(err.get(), ex);
        }
    }

    @Test
    void incompleteStrings() {
        var snippets = new String[]{
                "local a = [[#abcde#fg]",
                "local a = [a b cdef ]]§]|§§]]|§§]]]]|§",
                "local a = [§=|==§[a # a]§=]|| §]",
        };
        for (var s : expandOptions(snippets)) {
            var segments = s.split("#", -1);
            StringBuilder src = new StringBuilder();
            for (int i = 0; i < segments.length; i++) {
                src.append(segments[i]);
                if (i != 0) src.append(" ");
                final String src2 = src.toString();
                assertThrowsLexerOrParserException(() -> parse(src2), () -> "Code was " + src2);
            }
        }
    }

    @Test
    void returns() {
        for (var src : new String[]{
                "return false",
                "return false;",
                "do return false end",
                "do return false; end",
        })
            assertDoesNotThrow(() -> parse(src), () -> "Code: " + src);

        for (var src : new String[]{
                "return false;;",
                "return false;;;",
                "return false;;;;",
                "return false;;;;;",
        })
            assertThrows(LuaParserException.class, () -> parse(src), () -> "Code: " + src);
    }

    @Test
    void simpleCall() {
        Parser ps = new Parser("""
                print("hi")
                --some comment""");
        try {
            ps.parse();
        } catch (Exception e) {
            int i = 0;
        }
    }

    @Test
    void testGlobalVarargs() {
        var parser = parse("""
                table.pack(...)
                """);
    }

    @Test
    void varargAssignment() {
        assertThrowsLexerOrParserException(() -> parse("""
                ... = "test"
                """), () -> "");
    }



    @Test
    void forbiddenVararg() {
        assertThrows(LuaSemanticException.class, () -> parse("""
                function a(...)
                    function b()
                        tb = table.pack(...)
                    end
                end
                """));

        assertThrows(LuaSemanticException.class, () -> parse("""
                function b()
                    tb = table.pack(...)
                end
                """));
    }
}
