package dev.asdf00.jluavm.parsing.ir;

import dev.asdf00.jluavm.parsing.container.VarInfo;

public class CodeGenUtils {

    public static void genCloseVariable(StringBuilder sb, VarInfo info) {

    }

    public static String genCloseVariable(VarInfo info) {
        var sb = new StringBuilder();
        genCloseVariable(sb, info);
        return sb.toString();
    }
}
