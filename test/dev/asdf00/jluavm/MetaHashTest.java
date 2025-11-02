package dev.asdf00.jluavm;

import dev.asdf00.jluavm.internals.LUDTypeDescriptor;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

public class MetaHashTest {

    @Test
    public void verifyMetaHash() throws ReflectiveOperationException {
        var m = LUDTypeDescriptor.class.getDeclaredMethod("hashMetaKey", String.class);
        m.setAccessible(true);
        var set = new HashSet<Integer>();
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < LUDTypeDescriptor.META_KEYS.length; i++) {
            int hash = (int) (Integer) m.invoke(null, LUDTypeDescriptor.META_KEYS[i]);
            max = Math.max(max, hash);
            if (!set.add(hash)) {
                throw new IllegalStateException("found duplicate at " + i);
            }
            System.out.println("bits %s (%s) - %s".formatted(
                    padLeft(5, "0", Integer.toBinaryString(hash)),
                    padLeft(2, "0", String.valueOf(hash)),
                    LUDTypeDescriptor.META_KEYS[i]));
        }
        System.out.println("MaxHashValue = " + max);
    }

    public static String padLeft(int len, String pad, String msg) {
        return pad.repeat(Math.max(0, len - msg.length()) / pad.length()) + msg;
    }
}
