package dev.asdf00.jluavm.parsing;

import dev.asdf00.jluavm.parsing.LocalVariable.LocalVariableAccessor;
import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.ir.ast.AST;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SymbolTable {

    private Scope cur;

    public LocalVariableAccessor declareLocal(String name) {
        return declareLocal(name, 0);
    }

    public LocalVariableAccessor declareLocal(String name, int attributes) {
        var local = new LocalVariable(name, attributes);
        cur = new VariableScope(cur, local);
        return cur.findVar(name);
    }

    public LocalVariableAccessor findLocal(String name) {
        return cur.findVar(name);
    }

    public boolean isInsideLoop() {
        var c = cur;
        while (!(c instanceof FunctionScope)) {
            if (c instanceof BlockScope b && b.isLoop) {
                return true;
            }
            c = c.parent;
        }
        return false;
    }

    public boolean isEnvRedeclared() {
        return cur.redeclaredEnv;
    }

    /*-
     * =================================================================================================================
     *   SCOPE HANDLING     SCOPE HANDLING     SCOPE HANDLING     SCOPE HANDLING     SCOPE HANDLING     SCOPE HANDLING
     * =================================================================================================================
     */

    public AST.JumpDestinationNode resolveBreak() {
        // this method assumes that we already verified that we are in fact in a loop
        var c = cur;
        while (!(c instanceof BlockScope b && b.isLoop)) {
            c = c.parent;
        }
        return b.breakLabel;
    }

    public AST.JumpDestinationNode resolveJump(String name) {
        // TODO
        throw new UnsupportedOperationException("goto is not supported yet");
    }

    public void labelNotLast() {
        // TODO
    }

    public AST.JumpDestinationNode declareJumpLabel(String name) {
        // TODO
        throw new UnsupportedOperationException("jump labels are not supported yet");
    }

    /*-
     * =================================================================================================================
     *   SCOPE HANDLING     SCOPE HANDLING     SCOPE HANDLING     SCOPE HANDLING     SCOPE HANDLING     SCOPE HANDLING
     * =================================================================================================================
     */

    public LocalVariableAccessor[] closeScope() {
        return closeScope(null, null, null);
    }

    public LocalVariableAccessor[] closeFunctionScope(List<LocalVariableAccessor> functionArguments, List<LocalVariableAccessor> captured) {
        return closeScope(functionArguments, captured, null);
    }

    public LocalVariableAccessor[] closeLoopScope(AST.JumpDestinationNode[] breakLabelBox) {
        return closeScope(null, null, breakLabelBox);
    }

    public LocalVariableAccessor[] closeScope(List<LocalVariableAccessor> functionArguments, List<LocalVariableAccessor> captured, AST.JumpDestinationNode[] breakLabelBox) {
        var locals = new ArrayList<LocalVariableAccessor>();
        while (cur instanceof VariableScope varScope) {
            // close variable
            locals.add(varScope.accessVar());
            cur = cur.parent;
        }
        if (cur instanceof FunctionScope funcScope) {
            LocalVariableAccessor[] arguments = funcScope.accessAllArgs();
            Collections.addAll(locals, arguments);
            if (functionArguments != null) {
                Collections.addAll(functionArguments, arguments);
            }
            if (captured != null) {
                captured.addAll(funcScope.outerCaptureAccesses);
            }
        } else if (breakLabelBox != null && cur instanceof BlockScope b && b.isLoop) {
            assert breakLabelBox.length == 1 : "this is not a break label box";
            breakLabelBox[0] = b.breakLabel;
        }
        cur = cur.parent;
        return locals.toArray(LocalVariableAccessor[]::new);
    }

    public void openFunctionScope(boolean hasVarargs, String... paramNames) {
        var parameters = new LocalVariable[paramNames.length + (hasVarargs ? 1 : 0)];
        for (int i = 0; i < paramNames.length; i++) {
            parameters[i] = new LocalVariable(paramNames[i], 0);
        }
        if (hasVarargs) {
            parameters[parameters.length - 1] = new LocalVariable("...", 1);
        }
        cur = new FunctionScope(cur, hasVarargs, parameters);
    }

    public void openBlockScope() {
        cur = new BlockScope(cur, false);
    }

    public void openLoopScope() {
        cur = new BlockScope(cur, true);
    }

    /*-
     * =================================================================================================================
     *      SCOPING     SCOPING     SCOPING     SCOPING     SCOPING     SCOPING     SCOPING     SCOPING     SCOPING
     * =================================================================================================================
     */

    private static abstract sealed class Scope {
        final Scope parent;
        final boolean redeclaredEnv;
        final int localCnt;
        final ArrayList<AST.JumpDestinationNode> labels;

        Scope(Scope parent, boolean redeclaredEnv, int localCnt) {
            this.parent = parent;
            this.redeclaredEnv = redeclaredEnv;
            this.localCnt = localCnt;
            this.labels = new ArrayList<>();
        }

        LocalVariableAccessor findVar(String name) {
            return findVar(name, false);
        }

        abstract LocalVariableAccessor findVar(String name, boolean crossedFuncBorder);

        AST.JumpDestinationNode declareNamedLabel(String name) {
            var label = new AST.JumpDestinationNode(Position.UNKNOWN);
            labels.add(label);
            return label;
        }
    }

    private static final class FunctionScope extends Scope {
        final boolean hasVarargs;
        final LocalVariable[] parameters;
        boolean containsCaptured = false;
        final List<LocalVariableAccessor> captures = new ArrayList<>();
        final List<LocalVariableAccessor> outerCaptureAccesses = new ArrayList<>();

        FunctionScope(Scope parent, boolean hasVarargs, LocalVariable... parameters) {
            super(parent, parent == null ? false : parent.redeclaredEnv, parameters.length);
            this.hasVarargs = hasVarargs;
            this.parameters = parameters;
            for (int i = 0; i < parameters.length; i++) {
                parameters[i].argIdx = i;
            }
        }

        @Override
        LocalVariableAccessor findVar(String name, boolean crossedFuncBorder) {
            if ("...".equals(name)) {
                return hasVarargs ? parameters[parameters.length - 1].new LocalVariableAccessor() : null;
            }
            for (int i = parameters.length - 1; i >= 0; i--) {
                var p = parameters[i];
                if (p.name.equals(name)) {
                    containsCaptured |= crossedFuncBorder;
                    return p.new LocalVariableAccessor();
                }
            }
            if (parent == null) {
                // not found
                return null;
            }
            for (int i = captures.size() - 1; i >= 0; i--) {
                // go through previously captured values
                var c = captures.get(i);
                if (c.getVariable().name.equals(name)) {
                    // this is the same as a previous capture, branch off accessing from that
                    return c.clone();
                }
            }
            LocalVariableAccessor accessor = parent.findVar(name, true);
            if (accessor != null) {
                // this is an up-value
                if (captures.isEmpty()) {
                    // move all argument indices to reserve space for capturing
                    for (var p : parameters) {
                        p.argIdx++;
                    }
                }
                // snapshot the current state of the accessor for capturing (shallow)
                outerCaptureAccesses.add(accessor.clone());
                // generate access inside the function
                accessor.captureIdx = captures.size();
                accessor.upScopeDist++;
                accessor.getVariable().isCaptured = true;
                // cache this access
                captures.add(accessor.clone());
            }
            return accessor;
        }

        LocalVariableAccessor[] accessAllArgs() {
            var accesses = new LocalVariableAccessor[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                accesses[i] = parameters[i].new LocalVariableAccessor();
            }
            return accesses;
        }
    }

    private static final class BlockScope extends Scope {
        final boolean isLoop;

        AST.JumpDestinationNode breakLabel = new AST.JumpDestinationNode(Position.UNKNOWN);

        BlockScope(Scope parent, boolean isLoop) {
            super(parent, parent.redeclaredEnv, parent.localCnt);
            this.isLoop = isLoop;
        }

        @Override
        LocalVariableAccessor findVar(String name, boolean crossedFuncBorder) {
            return parent.findVar(name, crossedFuncBorder);
        }
    }

    private static final class VariableScope extends Scope {
        final LocalVariable variable;

        VariableScope(Scope parent, LocalVariable variable) {
            super(parent, variable.name.equals("_ENV") || parent.redeclaredEnv, parent.localCnt + 1);
            this.variable = variable;
            variable.setPositionInFrame(localCnt);
        }

        @Override
        LocalVariableAccessor findVar(String name, boolean crossedFuncBorder) {
            return variable.name.equals(name) ? accessVar() : parent.findVar(name, crossedFuncBorder);
        }

        LocalVariableAccessor accessVar() {
            return variable.new LocalVariableAccessor();
        }
    }

    /*-
     * =================================================================================================================
     *     DECLARED ELEMENTS     DECLARED ELEMENTS     DECLARED ELEMENTS     DECLARED ELEMENTS     DECLARED ELEMENTS
     * =================================================================================================================
     */
}