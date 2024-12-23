package dev.asdf00.jluavm.parsing.ir;

import dev.asdf00.jluavm.exceptions.loading.InternalLuaLoadingError;
import dev.asdf00.jluavm.internals.DelayedJavaCompiler;
import dev.asdf00.jluavm.parsing.container.LabelInfo;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.utils.Tuple;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Stack;
import java.util.function.Supplier;

@SuppressWarnings("FieldCanBeLocal")
public final class CompilationState {
    public static final boolean DEBUG_MODE = true;

    private final String COMPILED_CLASSES_MODULE_PREFIX = "dev.asdf00.jluavm.lualoaded.";

    private final Supplier<String> jClassNameGenerator;

    public final ArrayList<Tuple<String, String>> functionJavaCode = new ArrayList<>();
    public final ArrayList<ArrayList<Integer>> innerFunctionDependencies = new ArrayList<>();

    private final HashMap<LabelInfo, String> patches = new HashMap<>();
    private final ArrayList<Tuple<String, Integer>> patchResolutions = new ArrayList<>();

    private final Stack<FunctionScope> funcStack = new Stack<>();
    private FunctionScope curFunc = null;

    public CompilationState(Supplier<String> jClassNameGenerator) {
        this.jClassNameGenerator = jClassNameGenerator;
    }

    // =================================================================================================================
    // scope building helpers manipulation
    // =================================================================================================================

    public int clearEStack() {
        // returns the stack size before clearing
        return curFunc.getTop().clear();
    }

    public String pushEStack() {
        return curFunc.getTop().push();
    }

    public String popEStack() {
        return curFunc.getTop().pop();
    }

    public String peekEStack() {
        return "t" + curFunc.getTop().eStackPos;
    }

    public ArrayList<String> peekEStack(int cnt) {
        var peeks = new ArrayList<String>(cnt);
        int curStackPos = curFunc.getTop().eStackPos;
        if (curStackPos - cnt < -1) {
            throw new InternalLuaLoadingError("peeking beyond lower expression stack bound");
        }
        for (int i = curStackPos + 1 - cnt; i <= curStackPos; i++) {
            peeks.add("t" + i);
        }
        return peeks;
    }

    /**
     * Generate call, either external or internal.
     *
     * @param expectedResultCnt this call generates as many e-stack elements as requested (missing elements are filled
     *                          with NIL). If -1 is passed, all returned values are packed into one LuaArray.
     */
    public EStackCallInfo generateEStackCallInfo(int expectedResultCnt) {
        return curFunc.getTop().genCallInfo(expectedResultCnt);
    }

    public int getCurResumeLabel() {
        return curFunc.getTop().curResumeLabel;
    }

    public String openInnerBlock(int localsCnt) {
        return curFunc.pushInner(localsCnt);
    }

    public void closeInnerBlock(String content) {
        curFunc.popInner(content);
    }

    /**
     * @return the number of function definitions inside the current functions preceding this definition.
     */
    public int openFunction(int maxLocals, int argCnt, boolean hasParamsArg, int localsCnt) {
        int prevFuncCnt = curFunc != null ? curFunc.innerFuncs.size() : 0;
        funcStack.push(curFunc);
        curFunc = new FunctionScope(localsCnt, maxLocals, argCnt, hasParamsArg);
        return prevFuncCnt;
    }

    public void closeFunction(String content) {
        String name = jClassNameGenerator.get();
        String result = curFunc.generateJIC(name, content);
        int dept = functionJavaCode.size();
        functionJavaCode.add(new Tuple<>(name, result));
        innerFunctionDependencies.add(curFunc.innerFuncs);
        curFunc = funcStack.pop();
        if (curFunc != null) {
            curFunc.innerFuncs.add(dept);
        }
    }

    public String patchForLabel(LabelInfo info) {
        return patches.computeIfAbsent(info, i -> "### goto patch %d ###".formatted(patches.size()));
    }

    public void registerLabelPatchResolution(String patch, int resume) {
        patchResolutions.add(new Tuple<>(patch, resume));
    }

    public void resolveAllPatches() {
        for (int i = 0; i < functionJavaCode.size(); i++) {
            var clazz = functionJavaCode.get(i);
            String code = clazz.y();
            for (var r : patchResolutions) {
                code = code.replace(r.x(), "%d".formatted(r.y()));
            }
            functionJavaCode.set(i, new Tuple<>(clazz.x(), code));
        }
    }

    @SuppressWarnings("unchecked")
    public Constructor<? extends LuaFunction> loadAndLinkAllClasses() throws InternalLuaLoadingError {
        // compile and load generated classes
        var jClasses = (Class<? extends LuaFunction>[]) new Class<?>[functionJavaCode.size()];
        for (int i = 0; i < jClasses.length; i++) {
            var clsDef = functionJavaCode.get(i);
            var clazz = DelayedJavaCompiler.compileAndLoad(LuaFunction.class.getClassLoader(), COMPILED_CLASSES_MODULE_PREFIX + clsDef.x(), clsDef.y());
            if (!LuaFunction.class.isAssignableFrom(clazz)) {
                throw new InternalLuaLoadingError(clazz.getName() + " is not of type LuaFunction!");
            }
            jClasses[i] = (Class<? extends LuaFunction>) clazz;
        }
        // resolve linking related stuff via reflection
        var constructors = (Constructor<? extends LuaFunction>[]) new Constructor<?>[jClasses.length];
        var depts = new Field[jClasses.length];
        for (int i = 0; i < constructors.length; i++) {
            try {
                constructors[i] = jClasses[i].getDeclaredConstructor(LuaObject[].class, LuaObject[].class);
                depts[i] = jClasses[i].getDeclaredField("innerFunctions");
            } catch (ReflectiveOperationException e) {
                throw new InternalLuaLoadingError(e);
            }
        }
        // link classes
        for (int i = 0; i < constructors.length; i++) {
            try {
                var innerDepts = innerFunctionDependencies.get(i);
                var ds = (Constructor<? extends LuaFunction>[]) new Constructor<?>[innerDepts.size()];
                for (int j = 0; j < innerDepts.size(); j++) {
                    ds[j] = constructors[innerDepts.get(j)];
                }
                depts[i].set(null, ds);
            } catch (ReflectiveOperationException e) {
                throw new InternalLuaLoadingError(e);
            }
        }
        // return constructor for root function
        return constructors[constructors.length - 1];
    }

    // =================================================================================================================
    // inner classes
    // =================================================================================================================

    public record EStackCallInfo(int resumeLabel, String saveEStack) {
    }

    private static abstract class Scope {
        protected final int localsCount;
        public int eStackPos = -1;
        public int maxEStackPos = -1;
        public int maxEStackSavePos = -1;
        public int curResumeLabel = -1;
        protected final LinkedHashMap<String, ArrayList<Integer>> restoreHeaders = new LinkedHashMap<>();

        public Scope(int localsCount) {
            this.localsCount = localsCount;
        }

        // if this is >=0 when popping from the estack, we did an oopsie in code gen in regard to call info generation somewhere along the way
        protected int shouldHit = -1;

        public String push() {
            eStackPos++;
            maxEStackPos = Math.max(maxEStackPos, eStackPos);
            if (eStackPos >= shouldHit) {
                shouldHit = -1;
            }
            return "t" + eStackPos;
        }

        public String pop() {
            assert eStackPos >= 0;
            if (shouldHit >= 0) {
                throw new InternalLuaLoadingError("unexpected value '%d' for 'shouldHit'".formatted(shouldHit));
            }
            return "t" + (eStackPos--);
        }

        public int clear() {
            if (shouldHit >= 0) {
                throw new InternalLuaLoadingError("unexpected value '%d' for 'shouldHit'".formatted(shouldHit));
            }
            int size = eStackPos + 1;
            eStackPos = -1;
            return size;
        }

        public EStackCallInfo genCallInfo(int expectedReturns) {
            maxEStackSavePos = Math.max(maxEStackSavePos, eStackPos);
            curResumeLabel++;
            if (expectedReturns > 0) {
                shouldHit = eStackPos + expectedReturns;
            } else if (expectedReturns == -1) {
                // returns lua object array
                shouldHit = eStackPos + 1;
            }

            var saving = new StringBuilder();
            var restoring = new StringBuilder();
            if (eStackPos > -1) {
                saving.append("expressionStack[0] = t0;");
                restoring.append("t0 = expressionStack[0];");
            } else {
                saving.append("// nothing to save");
                restoring.append("// nothing to restore");
            }
            for (int i = 1; i <= eStackPos; i++) {
                saving.append("\nexpressionStack[").append(i).append("] = t").append(i).append(';');
                restoring.append("\n    t").append(i).append(" = expressionStack[").append(i).append("];");
            }
            for (int i = 0; i < expectedReturns; i++) {
                restoring.append("\n    t").append(eStackPos + 1 + i).append(" = returned.length > ").append(i)
                        .append(" ? returned[").append(i).append("] : LuaObject.nil();");
            }
            var bucket = restoreHeaders.computeIfAbsent(restoring.toString(), k -> new ArrayList<>());
            bucket.add(curResumeLabel);

            String stackSaver = saving.toString();
            return new EStackCallInfo(curResumeLabel, stackSaver);
        }

        protected String buildRestoreHeaders() {
            var restoring = new StringBuilder();
            for (var e : restoreHeaders.entrySet()) {
                var body = e.getKey();
                var vals = e.getValue();
                StringBuilder labels = new StringBuilder();
                for (int i = 0; i < vals.size(); i++) {
                    if (i > 0)
                        labels.append(",");

                    labels.append(vals.get(i));
                }

                restoring.append("\ncase ").append(labels).append(" -> {\n    ")
                        .append(body).append("\n}");
            }
            return restoring.toString();
        }

        protected final String eStackDefinitions() {
            var eStackDefinitions = new StringBuilder();
            eStackDefinitions.append("LuaObject t0 = null");
            for (int i = 1; i <= maxEStackPos; i++) {
                eStackDefinitions.append(", t").append(i).append(" = null");
            }
            eStackDefinitions.append(';');
            return eStackDefinitions.toString();
        }
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    private static final class InternalScope extends Scope {
        public final int scopeId;

        public InternalScope(int localsCount, int scopeId) {
            super(localsCount);
            this.scopeId = scopeId;
        }

        /**
         * Packs the content into a proper Java intermediate code method to be installed into a LuaFunction class.
         */
        public String generateJIC(String content) {
            if (shouldHit >= 0) {
                throw new InternalLuaLoadingError("unexpected value '%d' for 'shouldHit'".formatted(shouldHit));
            }
            if (eStackPos >= 0) {
                throw new InternalLuaLoadingError("unexpected value '%d' for 'eStackPos'".formatted(shouldHit));
            }

            String result = """
                    private void innerScope%d(LuaVM_RT vm, LuaObject[] stackFrame, LuaObject[] args, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
                    %s
                    switch (resume) {
                    case -1 -> {
                        expressionStack = %s;
                        vm.registerLocals(%d);
                    }
                    %s
                    default -> throw new InternalLuaRuntimeError("unknown resume point " + resume);
                    }
                    returned = null;
                    switch (resume) {
                    case -1:
                    %s
                    default: throw new InternalLuaRuntimeError("should not reach end of fall-through switch");
                    }
                    }""".formatted(scopeId, eStackDefinitions(),
                    maxEStackSavePos >= 0 ? "vm.registerExpressionStack(%d)".formatted(maxEStackSavePos + 1) : "null",
                    localsCount, buildRestoreHeaders(), content);
            return result;
        }
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    private static final class FunctionScope extends Scope {
        private final Stack<InternalScope> innerScopes = new Stack<>();
        private final ArrayList<String> methodList = new ArrayList<>();

        public final ArrayList<Integer> innerFuncs = new ArrayList<>();

        private final int maxLocalSize;
        private final int argCnt;
        private final boolean hasParamsArg;
        private int scopeCount = 0;

        public FunctionScope(int localsCount, int maxLocalSize, int argCnt, boolean hasParamsArg) {
            super(localsCount);
            this.maxLocalSize = maxLocalSize;
            this.argCnt = argCnt;
            this.hasParamsArg = hasParamsArg;
        }

        public Scope getTop() {
            return innerScopes.isEmpty() ? this : innerScopes.peek();
        }

        public void popInner(String content) {
            methodList.add(innerScopes.pop().generateJIC(content));
        }

        public String pushInner(int localsCnt) {
            return "innerScope" + innerScopes.push(new InternalScope(localsCnt, scopeCount++)).scopeId;
        }

        public String generateJIC(String jClassName, String content) {
            if (shouldHit >= 0) {
                throw new InternalLuaLoadingError("unexpected value '%d' for 'shouldHit'".formatted(shouldHit));
            }
            if (eStackPos >= 0) {
                throw new InternalLuaLoadingError("unexpected value '%d' for 'eStackPos'".formatted(shouldHit));
            }
            if (!innerScopes.isEmpty()) {
                throw new InternalLuaLoadingError("trying to generate java intermediate code with inner scopes still unfinished");
            }

            // @formatter:off
            String result = """
                    package dev.asdf00.jluavm.lualoaded;
                    
                    import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;
                    import dev.asdf00.jluavm.exceptions.LuaRuntimeError;
                    import dev.asdf00.jluavm.internals.LuaVM_RT;
                    import dev.asdf00.jluavm.runtime.types.*;
                    import dev.asdf00.jluavm.runtime.utils.*;
                    
                    import java.lang.reflect.Constructor;
                    import java.util.Arrays;
                    
                    public final class %s extends LuaFunction {
                    public static Constructor<? extends LuaFunction>[] innerFunctions;
                    
                    public %s(LuaObject[] _ENV, LuaObject[] closures) {
                        super(_ENV, closures);
                    }
                    
                    @Override
                    public int getMaxLocalsSize() {
                        return %d;
                    }
                    
                    @Override
                    public int getArgCount() {
                        return %d;
                    }
                    
                    @Override
                    public boolean hasParamsArg() {
                        return %s;
                    }
                    
                    @Override
                    public void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
                    %s
                    switch (resume) {
                    case -1 -> {
                        expressionStack = %s;
                        vm.registerLocals(%d);
                    }
                    %s
                    default -> throw new InternalLuaRuntimeError("unknown resume point " + resume);
                    }
                    returned = null;
                    switch (resume) {
                    case -1:
                    %s
                    default: throw new InternalLuaRuntimeError("should not reach end of fall-through switch");
                    }
                    }
                    
                    // inner scopes
                    %s
                    }""".formatted(jClassName, jClassName,
                    maxLocalSize, argCnt, hasParamsArg ? "true" : "false",
                    eStackDefinitions(), maxEStackSavePos >= 0 ? "vm.registerExpressionStack(%d)".formatted(maxEStackSavePos + 1) : "null", localsCount,
                    buildRestoreHeaders(),
                    content,
                    String.join("\n\n", methodList));
            // @formatter:on
            return result;
        }
    }
}
