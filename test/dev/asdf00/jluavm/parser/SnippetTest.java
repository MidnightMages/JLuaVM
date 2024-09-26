package dev.asdf00.jluavm.parser;

import dev.asdf00.jluavm.Constants;
import dev.asdf00.jluavm.parsing.Parser;
import dev.asdf00.jluavm.parsing.exceptions.LuaParserException;
import org.junit.jupiter.api.Test;

import static dev.asdf00.jluavm.Constants.largeValidLuaProgram;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SnippetTest {

    private Parser parse(String code){
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
}
