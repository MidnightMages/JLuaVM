package dev.asdf00.jluavm.vm;

import dev.asdf00.jluavm.runtime.types.LuaObject;
import org.junit.jupiter.api.Test;

public class PatternMatchingTest extends VmTest {
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
}
