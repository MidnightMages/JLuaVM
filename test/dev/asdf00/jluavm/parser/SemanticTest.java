package dev.asdf00.jluavm.parser;

import dev.asdf00.jluavm.exceptions.LuaLoadingException;
import dev.asdf00.jluavm.exceptions.loading.LuaSemanticException;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static dev.asdf00.jluavm.Util.expandOptions;
import static org.junit.jupiter.api.Assertions.fail;

public class SemanticTest {
    static void assertThrowsForExpSnippets(Class<? extends LuaLoadingException>[] allowedExceptions, String[] snippets, Consumer<String> r) {
        for (var s : expandOptions(snippets)) {
            try {
                r.accept(s);
            }
            catch (LuaLoadingException ex) {
                boolean rethrow = true;
                for (var e2 : allowedExceptions) {
                    if (e2.isAssignableFrom(ex.getClass())) {
                        rethrow = false;
                        break;
                    }
                }
                if (rethrow)
                    fail("No exception was thrown, but one was supposed to be. Code was: '" + s + "'", ex);
            }
        }
    }

    @Test
    void forbiddenStatements() {
        //noinspection unchecked
        assertThrowsForExpSnippets(new Class[]{LuaSemanticException.class}, new String[]{
                "break§| §;", // TODO this one should failing
        }, ParserTest::parse);
    }
}
