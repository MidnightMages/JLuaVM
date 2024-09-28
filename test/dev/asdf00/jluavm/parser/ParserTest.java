package dev.asdf00.jluavm.parser;

import dev.asdf00.jluavm.parsing.Parser;
import dev.asdf00.jluavm.parsing.SymTable;
import dev.asdf00.jluavm.parsing.exceptions.LuaParserException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;

import static dev.asdf00.jluavm.Constants.largeValidLuaProgram;
import static dev.asdf00.jluavm.Util.expandOptions;
import static org.junit.jupiter.api.Assertions.*;

public class ParserTest {

    private Parser parse(String code) {
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
        } catch (ReflectiveOperationException e) {
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

    @Test
    void testClosure() {
        var parser = parse("""
                local a = 1
                local b = 2
                local function c(d, e, ...)
                    a = "test"
                end
                """);
        assertEquals("VarScope {parent=-1, id=0, funcBorder=false, closable=false, names={" +
                "a=VarInfo{jName='_0$a', isGlobal=false, isConstant=false, isClosable=false, isInClosure=true, isWritten=true}, " +
                "b=VarInfo{jName='_0$b', isGlobal=false, isConstant=false, isClosable=false, isInClosure=false, isWritten=false}, " +
                "c=VarInfo{jName='_0$c', isGlobal=false, isConstant=false, isClosable=false, isInClosure=false, isWritten=false}}, " +
                "children=[VarScope {parent=0, id=1, funcBorder=true, closable=false, names={" +
                "d=VarInfo{jName='_1$d', isGlobal=false, isConstant=false, isClosable=false, isInClosure=false, isWritten=false}, " +
                "e=VarInfo{jName='_1$e', isGlobal=false, isConstant=false, isClosable=false, isInClosure=false, isWritten=false}}, " +
                "children=[]}]}", getSymTab(parser));
    }
}
