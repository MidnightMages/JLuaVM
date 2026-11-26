package dev.asdf00.jluavm.interpreter;

import dev.asdf00.jluavm.internals.bc.BytecodeGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.HashSet;

public class CodeSanityCheckTest {
    @Test
    void ensureBytecodeUniqueness() {
        var allFields = BytecodeGenerator.BytecodeOperations.class.getFields();
        HashSet<Byte> seenBytecodeIds = new HashSet<>();
        for (var field : allFields) {
            try {
                Assertions.assertTrue(Modifier.isStatic(field.getModifiers()));
                Assertions.assertTrue(Modifier.isFinal(field.getModifiers()));
                var currId = field.get(null);
                if (currId instanceof Byte b) {
                    Assertions.assertTrue(seenBytecodeIds.add(b), "duplicate bytecode id '%s'! The second occurrence was '%s'.".formatted(currId, field.getName()));
                } else {
                    Assertions.fail("field %s does not appear to be of datatype byte!".formatted(field.getName()));
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
