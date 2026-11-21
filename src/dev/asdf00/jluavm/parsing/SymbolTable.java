package dev.asdf00.jluavm.parsing;

import dev.asdf00.jluavm.exceptions.loading.LuaParserException;
import dev.asdf00.jluavm.parsing.container.LocalVariable;
import dev.asdf00.jluavm.parsing.container.LocalVariable.LocalVariableAccessor;
import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.ir.CFGBuilder;
import dev.asdf00.jluavm.parsing.ir.JumpLabel;
import dev.asdf00.jluavm.parsing.ir.Terminator.Jump;
import dev.asdf00.jluavm.runtime.utils.Singletons;

import java.util.ArrayList;

/**
 * Keeps track of functions, blocks, local variables, labels and gotos during parsing.
 * <p>
 * Every block holds its local variables and labels in a single list of {@link Declaration}s in source order (the
 * function body block starts with the parameters). A point in the program is identified by a block and the number of
 * declarations in that block preceding it. Leaving a scope, checking gotos against the scope of locals and computing
 * which variables to close all boil down to walking the declarations between two such points.
 */
public class SymbolTable {

    private FunctionScope curFunc;

    /*-
     * =================================================================================================================
     *   LOCAL VARIABLES     LOCAL VARIABLES     LOCAL VARIABLES     LOCAL VARIABLES     LOCAL VARIABLES
     * =================================================================================================================
     */

    public LocalVariableAccessor declareLocal(String name) {
        return declareLocal(name, LocalVariable.Attributes.NONE);
    }

    public LocalVariableAccessor declareLocal(String name, int attributes) {
        assert !curFunc.innermostBlock.hasTrailingLabels() : "labelNotLast() not called before local declaration";
        var local = new LocalVariable(name, attributes);
        curFunc.innermostBlock.declareLocal(local);
        return local.new LocalVariableAccessor();
    }

    /**
     * @return an accessor to the variable or null if there is no local variable of this name
     */
    public LocalVariableAccessor findLocal(String name) {
        return resolve(curFunc, name);
    }

    public boolean isEnvRedeclared() {
        for (var f = curFunc; f != null; f = f.parent) {
            if (f.findActiveLocal("_ENV") != null) {
                return true;
            }
        }
        return false;
    }

    private static LocalVariableAccessor resolve(FunctionScope f, String name) {
        var local = f.findActiveLocal(name);
        if (local != null) {
            return local.new LocalVariableAccessor();
        }
        if (f.parent == null || "...".equals(name)) {
            // varargs are never captured
            return null;
        }

        // not found as a local in current function scope
        for (var capture : f.captures) {
            if (capture.getVariable().name.equals(name)) {
                // we already captured this name (captured names are inherently unique)
                return capture.clone();
            }
        }

        // not found in already captured elements, continuing upwards
        var outer = resolve(f.parent, name);
        if (outer == null) {
            // no matching name found
            return null;
        }
        /*
         * We found a matching element to capture.
         * We store the outer accessor for closure creation and
         * adjust the inner accessor for using the captured variable.
         */
        f.outerCaptureAccesses.add(outer);
        // building the captured local access
        var capture = outer.clone();
        capture.captureIdx = f.captures.size();
        capture.upScopeDist++;
        capture.getVariable().isCaptured = true;
        f.captures.add(capture);
        return capture.clone();
    }

    /*-
     * =================================================================================================================
     *   JUMPS AND LABELS     JUMPS AND LABELS     JUMPS AND LABELS     JUMPS AND LABELS     JUMPS AND LABELS
     * =================================================================================================================
     */

    public boolean isInsideLoop() {
        return innermostLoop() != null;
    }

    private BlockScope innermostLoop() {
        for (var b = curFunc.innermostBlock; b != null; b = b.parent) {
            if (b.isLoop()) {
                return b;
            }
        }
        return null;
    }

    /**
     * @return the break label of the innermost loop
     */
    public JumpLabel resolveBreak() {
        var loop = innermostLoop();
        assert loop != null : "break outside of loop";
        return loop.breakLabel;
    }

    /**
     * Declares a named jump label in the current block. The label is only placed in the CFG once it is known whether
     * it is the last statement of its block (see {@link #labelNotLast(CFGBuilder)} and scope closing).
     */
    public void declareJumpLabel(String name, Position pos) {
        var existing = findVisibleLabel(name);
        if (existing != null) {
            throw new LuaParserException(pos, "label '%s' already defined on line %d".formatted(name, existing.label().label.pos.line()));
        }
        curFunc.innermostBlock.declarations.add(new LabelDeclaration(new JumpLabel(name, pos), curFunc.innermostBlock.declarations.size()));
    }

    /**
     * Must be called before every non-void statement (i.e. anything but labels and ';'). Places all labels that are
     * followed by this statement at the current position in the CFG.
     */
    public void labelNotLast(CFGBuilder cb) {
        curFunc.innermostBlock.placeTrailingLabels(cb, false);
    }

    /**
     * Emits the jump of a goto. Backward jumps are resolved immediately. Forward jumps target a placeholder label and
     * get their close list once the goto is resolved when a block is closed.
     */
    public void resolveJump(CFGBuilder cb, String name, Position pos) {
        assert !curFunc.innermostBlock.hasTrailingLabels() : "labelNotLast() not called before goto";
        var visible = findVisibleLabel(name);
        if (visible != null) {
            // backward jump, no local can be declared between label and goto in the label's block
            cb.jump(visible.label().label, closablesBetween(curFunc.innermostBlock, curFunc.innermostBlock.declarations.size(), visible.block(), visible.label().blockIndex));
            return;
        }
        // forward jump
        var placeholder = new JumpLabel(name, pos);
        Jump jump = cb.jump(placeholder, Singletons.EMPTY_LOCAL_ACCESSOR_ARRAY);
        curFunc.innermostBlock.floatingGotos.add(new FloatingGoto(name, pos, placeholder, jump, curFunc.innermostBlock, curFunc.innermostBlock.declarations.size()));
    }

    private record VisibleLabel(BlockScope block, LabelDeclaration label) {
    }

    private VisibleLabel findVisibleLabel(String name) {
        for (var b = curFunc.innermostBlock; b != null; b = b.parent) {
            var label = b.findLabel(name);
            if (label != null) {
                return new VisibleLabel(b, label);
            }
        }
        return null;
    }

    /*-
     * =================================================================================================================
     *   CLOSABLES         CLOSABLES         CLOSABLES         CLOSABLES         CLOSABLES         CLOSABLES
     * =================================================================================================================
     */

    /**
     * @return all closable variables that are left by a break, in closing order
     */
    public LocalVariableAccessor[] closablesUntilLoop() {
        var loop = innermostLoop();
        assert loop != null : "break outside of loop";
        return closablesBetween(curFunc.innermostBlock, curFunc.innermostBlock.declarations.size(), loop, 0);
    }

    /**
     * @return all closable variables that are left by a return, in closing order
     */
    public LocalVariableAccessor[] closablesInFunction() {
        var body = curFunc.innermostBlock;
        while (body.parent != null) {
            body = body.parent;
        }
        return closablesBetween(curFunc.innermostBlock, curFunc.innermostBlock.declarations.size(), body, 0);
    }

    /**
     * Collects the closable variables that are left when going from one point in the program outwards to another.
     *
     * @param from      block of the starting point
     * @param fromIndex number of declarations in {@code from} preceding the starting point
     * @param to        block of the target point, must be {@code from} or one of its ancestors
     * @param toIndex   number of declarations in {@code to} preceding the target point
     * @return all closable variables declared between both points, in closing order
     */
    private static LocalVariableAccessor[] closablesBetween(BlockScope from, int fromIndex, BlockScope to, int toIndex) {
        var closables = new ArrayList<LocalVariableAccessor>();
        var b = from;
        int end = fromIndex;
        while (true) {
            int start = b == to ? toIndex : 0;
            for (int i = end - 1; i >= start; i--) {
                if (b.declarations.get(i) instanceof LocalDeclaration(var local) && local.isClosable) {
                    closables.add(local.new LocalVariableAccessor());
                }
            }
            if (b == to) {
                return closables.toArray(LocalVariableAccessor[]::new);
            }
            end = b.indexInParent;
            b = b.parent;
            assert b != null : "target block is not an ancestor";
        }
    }

    /*-
     * =================================================================================================================
     *   SCOPE HANDLING     SCOPE HANDLING     SCOPE HANDLING     SCOPE HANDLING     SCOPE HANDLING     SCOPE HANDLING
     * =================================================================================================================
     */

    public void openFunctionScope(boolean hasVarargs, String... paramNames) {
        var parameters = new LocalVariable[paramNames.length + (hasVarargs ? 1 : 0)];
        for (int i = 0; i < paramNames.length; i++) {
            assert !"...".equals(paramNames[i]) : "vararg parameter must be declared via hasVarargs";
            parameters[i] = new LocalVariable(paramNames[i], LocalVariable.Attributes.NONE);
        }
        if (hasVarargs) {
            parameters[parameters.length - 1] = new LocalVariable("...", LocalVariable.Attributes.CONSTANT);
        }
        curFunc = new FunctionScope(curFunc, parameters);
        curFunc.innermostBlock = new BlockScope(null, false);
        for (var p : parameters) {
            curFunc.innermostBlock.declareLocal(p);
        }
    }

    public void openBlockScope() {
        curFunc.innermostBlock = new BlockScope(curFunc.innermostBlock, false);
    }

    public void openLoopScope() {
        curFunc.innermostBlock = new BlockScope(curFunc.innermostBlock, true);
    }

    public void closeScope(CFGBuilder cb) {
        assert curFunc.innermostBlock.parent != null : "use closeFunctionScope() for function bodies";
        assert !curFunc.innermostBlock.isLoop() : "use closeLoopScope() for loops";
        closeBlock(cb);
    }

    /**
     * @return the break label of the closed loop, to be placed after the loop
     */
    public JumpLabel closeLoopScope(CFGBuilder cb) {
        assert curFunc.innermostBlock.isLoop() : "not a loop scope";
        return closeBlock(cb).breakLabel;
    }

    public ClosedFunction closeFunctionScope(CFGBuilder cb) {
        assert curFunc.innermostBlock.parent == null : "unclosed blocks in function";
        closeBlock(cb);
        // captured values are passed in front of the arguments
        int argOffset = curFunc.captures.isEmpty() ? 0 : 1;
        var arguments = new LocalVariableAccessor[curFunc.parameters.length];
        for (int i = 0; i < arguments.length; i++) {
            curFunc.parameters[i].argIdx = i + argOffset;
            arguments[i] = curFunc.parameters[i].new LocalVariableAccessor();
        }
        var closed = new ClosedFunction(arguments, curFunc.outerCaptureAccesses.toArray(LocalVariableAccessor[]::new));
        curFunc = curFunc.parent;
        return closed;
    }

    public record ClosedFunction(LocalVariableAccessor[] arguments, LocalVariableAccessor[] captures) {
    }

    /**
     * Closes the current block. To-be-closed variables are closed on the edge leaving the block, labels at the end of
     * the block are placed after it and floating gotos are resolved or moved to the enclosing block.
     *
     * @return the closed block
     */
    private BlockScope closeBlock(CFGBuilder cb) {
        var block = curFunc.innermostBlock;
        cb.exitScope(closablesBetween(block, block.declarations.size(), block, 0));
        // labels at the end of a block target whatever follows the block, therefore none of its locals are active
        block.placeTrailingLabels(cb, true);
        resolveFloatingGotos(cb, block);
        curFunc.innermostBlock = block.parent;
        return block;
    }

    private void resolveFloatingGotos(CFGBuilder cb, BlockScope block) {
        for (var g : block.floatingGotos) {
            var label = block.findLabel(g.name);
            if (label == null) {
                // no label found
                if (block.parent == null) {
                    throw new LuaParserException(g.pos, "no visible label '%s' for goto at line %d".formatted(g.name, g.pos.line()));
                } else {
                    // maybe the label is declared later in an enclosing block
                    g.blockIndex = block.indexInParent;
                    block.parent.floatingGotos.add(g);
                    continue;
                }
            }
            if (!label.isLast) {
                // we verify that we do not jump into the scope of a local variable
                assert label.blockIndex >= g.blockIndex : "label '%s' should have been resolved as backward jump".formatted(g.name);
                for (int i = g.blockIndex; i < label.blockIndex; i++) {
                    if (block.declarations.get(i) instanceof LocalDeclaration(var local)) {
                        throw new LuaParserException(g.pos, "<goto %s> at line %d jumps into the scope of local '%s'"
                                .formatted(g.name, g.pos.line(), local.name));
                    }
                }
            }
            // a last label is behind the block, so all locals of the block preceding the goto are left as well
            g.jump.toClose = closablesBetween(g.originBlock, g.originIndex, block, label.isLast ? 0 : g.blockIndex);
            cb.mergeLabel(g.placeholder, label.label);
        }
        block.floatingGotos.clear();
    }

    /*-
     * =================================================================================================================
     *   DATA CONTAINERS      DATA CONTAINERS      DATA CONTAINERS      DATA CONTAINERS      DATA CONTAINERS
     * =================================================================================================================
     */

    private static final class FunctionScope {
        final FunctionScope parent;
        final LocalVariable[] parameters;
        final ArrayList<LocalVariableAccessor> captures = new ArrayList<>();
        final ArrayList<LocalVariableAccessor> outerCaptureAccesses = new ArrayList<>();
        BlockScope innermostBlock;

        FunctionScope(FunctionScope parent, LocalVariable[] parameters) {
            this.parent = parent;
            this.parameters = parameters;
        }

        /**
         * @return the innermost local variable with the given name that is in the current function, or null
         */
        LocalVariable findActiveLocal(String name) {
            int end = innermostBlock.declarations.size();
            for (var b = innermostBlock; b != null; end = b.indexInParent, b = b.parent) {
                for (int i = end - 1; i >= 0; i--) {
                    if (b.declarations.get(i) instanceof LocalDeclaration(var local) && local.name.equals(name)) {
                        return local;
                    }
                }
            }
            return null;
        }
    }

    private static final class BlockScope {
        final BlockScope parent;
        final int indexInParent;
        final int frameBase;
        final JumpLabel breakLabel;
        final ArrayList<Declaration> declarations = new ArrayList<>();
        int firstTrailing = 0;
        int localCount = 0;
        final ArrayList<FloatingGoto> floatingGotos = new ArrayList<>();

        BlockScope(BlockScope parent, boolean isLoop) {
            this.parent = parent;
            this.indexInParent = parent == null ? 0 : parent.declarations.size();
            this.frameBase = parent == null ? 0 : parent.frameBase + parent.localCount;
            this.breakLabel = isLoop ? new JumpLabel(Position.UNKNOWN) : null;
        }

        boolean isLoop() {
            return breakLabel != null;
        }

        void declareLocal(LocalVariable local) {
            local.setPositionInFrame(frameBase + localCount);
            localCount++;
            declarations.add(new LocalDeclaration(local));
            firstTrailing = declarations.size();
        }

        boolean hasTrailingLabels() {
            return firstTrailing < declarations.size();
        }

        void placeTrailingLabels(CFGBuilder cb, boolean isLast) {
            for (int i = firstTrailing; i < declarations.size(); i++) {
                var label = (LabelDeclaration) declarations.get(i);
                cb.label(label.label);
                label.isLast = isLast;
            }
            firstTrailing = declarations.size();
        }

        LabelDeclaration findLabel(String name) {
            for (var d : declarations) {
                if (d instanceof LabelDeclaration label && label.label.name.equals(name)) {
                    return label;
                }
            }
            return null;
        }
    }

    private sealed interface Declaration permits LocalDeclaration, LabelDeclaration {
    }

    private record LocalDeclaration(LocalVariable local) implements Declaration {
    }

    private static final class LabelDeclaration implements Declaration {
        final JumpLabel label;
        final int blockIndex;
        boolean isLast = false;

        LabelDeclaration(JumpLabel label, int blockIndex) {
            this.label = label;
            this.blockIndex = blockIndex;
        }
    }

    /**
     * A forward goto. {@link #jump} still targets {@link #placeholder} and gets its close list on resolution.
     */
    private static final class FloatingGoto {
        final String name;
        final Position pos;
        final JumpLabel placeholder;
        final Jump jump;
        final BlockScope originBlock;
        /**
         * Number of declarations in {@link #originBlock} preceding the goto
         */
        final int originIndex;
        /**
         * Number of declarations preceding the goto in the block currently holding it, lowered when the goto leaves
         * a block unresolved
         */
        int blockIndex;

        FloatingGoto(String name, Position pos, JumpLabel placeholder, Jump jump, BlockScope originBlock, int originIndex) {
            this.name = name;
            this.pos = pos;
            this.placeholder = placeholder;
            this.jump = jump;
            this.originBlock = originBlock;
            this.originIndex = originIndex;
            this.blockIndex = originIndex;
        }
    }
}
