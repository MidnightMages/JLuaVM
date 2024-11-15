package dev.asdf00.jluavm.parsing.ir;

import java.util.ArrayList;

public class CompilationState {
    // result
    public final ArrayList<String> functionJavaCode = new ArrayList<>();
    public final ArrayList<ArrayList<Integer>> innerFunctionDependencies = new ArrayList<>();


    // =================================================================================================================
    // scope building helpers manipulation
    // =================================================================================================================

    public int clearEStack() {
        // returns the stack size before clearing
        return 0;
    }

    public String pushEStack() {
        return "";
    }

    public String popEStack() {
        return "";
    }

    public String peekEStack() {
        return "";
    }

    public ArrayList<String> peekEStack(int cnt) {
        return null;
    }

    /**
     * Generate call, either external or internal.
     * @param expectedResultCnt this call generates as many e-stack elements as requested (missing elements are filled
     *                          with NIL). If -1 is passed, all returned values are packed into one LuaArray.
     * @return
     */
    public EStackCallInfo generateEStackCallInfo(int expectedResultCnt) {
        return new EStackCallInfo(0, "");
    }

    public int getMaxResumeLabel() {
        return 0;
    }

    public String openInnerBlock(int localsCnt, boolean loop) {
        return "blockName";
    }

    public void closeInnerBlock(String content) {

    }

    public String openFunction(int maxLocals, int argCnt, boolean hasParamsArg, int localsCnt) {
        return "ClassNameOfLuaFunction";
    }

    public void closeFunction(String content) {

    }

    public void registerInnerClass(String name) {

    }

    public void registerGotoPatch(String patch, int resume) {

    }

    public record EStackCallInfo(int resumeLabel, String saveEStack) {
    }
}
