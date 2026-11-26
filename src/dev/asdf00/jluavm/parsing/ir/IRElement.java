package dev.asdf00.jluavm.parsing.ir;

import dev.asdf00.jluavm.parsing.container.LocalVariable.LocalVariableAccessor;
import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.container.Token;
import dev.asdf00.jluavm.parsing.container.TokenType;
import dev.asdf00.jluavm.utils.Tuple;

import java.util.HashMap;

/**
 * Statements and expressions stored inside the {@link BasicBlock}s of a {@link ControlFlowGraph}. Control flow itself
 * is not represented by these nodes but by the {@link Terminator}s of the blocks.
 */
public abstract sealed class IRElement {
    public final Position pos;

    public IRElement(Position pos) {
        this.pos = pos;
    }

    @Override
    public String toString() {
        return "%s@%s".formatted(getClass().getSimpleName(), pos.sourcePos());
    }

    /*-
     * =================================================================================================================
     *    STATEMENTS     STATEMENTS     STATEMENTS     STATEMENTS     STATEMENTS     STATEMENTS     STATEMENTS
     * =================================================================================================================
     */

    public static final class ErrorNode extends IRElement {
        public final IRElement msg;

        public ErrorNode(Position pos, IRElement msg) {
            super(pos);
            this.msg = msg;
        }
    }

    public static final class LocalDeclarationNode extends IRElement {
        public final LocalVariableAccessor local;

        public LocalDeclarationNode(Position pos, LocalVariableAccessor local) {
            super(pos);
            this.local = local;
        }
    }

    public static final class AssignmentNode extends IRElement {
        public final IRElement[] locations;
        public final IRElement[] expressions;

        public AssignmentNode(Position pos, IRElement[] locations, IRElement... expressions) {
            super(pos);
            for (int i = 0; i < locations.length; i++) {
                if (locations[i] instanceof LocalAccessNode access) {
                    access.local.getVariable().setWritten(pos);
                }
            }
            this.locations = locations;
            this.expressions = expressions;
        }

        public AssignmentNode(Position pos, IRElement location, IRElement... expressions) {
            this(pos, new IRElement[]{location}, expressions);
        }
    }

    public static final class CheckClosableNode extends IRElement {
        public final LocalVariableAccessor variable;

        public CheckClosableNode(Position pos, LocalVariableAccessor variable) {
            super(pos);
            assert variable.getVariable().isClosable : "%s is not to-be-closed".formatted(variable.getVariable().name);
            this.variable = variable;
        }
    }

    /*-
     * =================================================================================================================
     *    EXPRESSIONS     EXPRESSIONS     EXPRESSIONS     EXPRESSIONS     EXPRESSIONS     EXPRESSIONS     EXPRESSIONS
     * =================================================================================================================
     */

    public static final class FunctionDefNode extends IRElement {
        public final boolean isChunk;
        public final LocalVariableAccessor[] captures;
        public final LocalVariableAccessor[] accessors;
        public final ControlFlowGraph cfg;

        public FunctionDefNode(Position pos, boolean isChunk, LocalVariableAccessor[] captures,
                               LocalVariableAccessor[] accessors, ControlFlowGraph cfg) {
            super(pos);
            this.isChunk = isChunk;
            this.captures = captures;
            this.accessors = accessors;
            this.cfg = cfg;
        }
    }

    public static final class EnvAccessNode extends IRElement {
        public EnvAccessNode(Position pos) {
            super(pos);
        }
    }

    public static final class AdjustNumberOfValuesNode extends IRElement {
        public final int requested;
        public final IRElement multivalue;

        public AdjustNumberOfValuesNode(Position pos, int requested, IRElement multivalue) {
            super(pos);
            this.requested = requested;
            this.multivalue = multivalue;
        }

        public AdjustNumberOfValuesNode(int requested, IRElement multivalue) {
            this(multivalue.pos, requested, multivalue);
        }
    }

    public static final class ToNumberOrFailNode extends IRElement {
        public final IRElement value;
        public final String failFormat;

        public ToNumberOrFailNode(Position pos, IRElement value, String failFormat) {
            super(pos);
            this.value = value;
            this.failFormat = failFormat;
        }
    }

    public static final class FunctionCallNode extends IRElement {
        public final IRElement callable;
        public final IRElement[] parameters;

        public FunctionCallNode(Position pos, IRElement callable, IRElement... parameters) {
            super(pos);
            this.callable = callable;
            this.parameters = parameters;
        }
    }

    public static final class OopCallNode extends IRElement {
        public final IRElement env;
        public final IRElement obj;
        public final IRElement key;
        public final IRElement[] parameters;

        public OopCallNode(Position pos, IRElement env, IRElement obj, IRElement key, IRElement... parameters) {
            super(pos);
            this.env = env;
            this.obj = obj;
            this.key = key;
            this.parameters = parameters;
        }
    }

    public static final class IndexedAccessNode extends IRElement {
        public final IRElement obj;
        public final IRElement key;

        public IndexedAccessNode(Position pos, IRElement obj, IRElement key) {
            super(pos);
            this.obj = obj;
            this.key = key;
        }
    }

    public static final class LocalAccessNode extends IRElement {
        public final LocalVariableAccessor local;

        public LocalAccessNode(Position pos, LocalVariableAccessor local) {
            super(pos);
            this.local = local;
        }
    }

    public static final class OrNode extends IRElement {
        public final IRElement left;
        public final IRElement right;

        public OrNode(Position pos, IRElement left, IRElement right) {
            super(pos);
            this.left = left;
            this.right = right;
        }
    }

    public static final class AndNode extends IRElement {
        public final IRElement left;
        public final IRElement right;

        public AndNode(Position pos, IRElement left, IRElement right) {
            super(pos);
            this.left = left;
            this.right = right;
        }
    }

    public static final class NotNode extends IRElement {
        public final IRElement value;

        public NotNode(Position pos, IRElement value) {
            super(pos);
            this.value = value;
        }
    }

    public static final class BinaryOperationNode extends IRElement {
        public final TokenType op;
        public final IRElement left;
        public final IRElement right;

        public BinaryOperationNode(Position pos, TokenType op, IRElement left, IRElement right) {
            super(pos);
            this.op = op;
            this.left = left;
            this.right = right;
        }
    }

    public static final class UnaryOperationNode extends IRElement {
        public final TokenType op;
        public final IRElement value;

        public UnaryOperationNode(Position pos, TokenType op, IRElement value) {
            super(pos);
            this.op = op;
            this.value = value;
        }
    }

    public static final class ConstantNode extends IRElement {
        public final ConstantType type;
        public final Object data;

        private ConstantNode(Position pos, ConstantType type, Object data) {
            super(pos);
            this.type = type;
            this.data = data;
        }

        public static ConstantNode ofString(Token sourceToken) {
            assert sourceToken.type() == TokenType.LITERAL_STRING;
            return ofString(sourceToken.pos(), sourceToken.stVal());
        }

        public static ConstantNode ofIdent(Token sourceToken) {
            assert sourceToken.type() == TokenType.IDENT;
            return ofIdent(sourceToken.pos(), sourceToken.stVal());
        }

        public static ConstantNode ofInteger(Token sourceToken) {
            assert sourceToken.type() == TokenType.NUMERAL;
            assert sourceToken.lVal() >= 0;
            return ofInteger(sourceToken.pos(), sourceToken.lVal());
        }

        public static ConstantNode ofDouble(Token sourceToken) {
            assert sourceToken.type() == TokenType.NUMERAL;
            assert sourceToken.nVal() >= 0;
            return new ConstantNode(sourceToken.pos(), ConstantType.Double, sourceToken.nVal());
        }

        public static ConstantNode ofBoolean(Token sourceToken, boolean value) {
            assert sourceToken.type() == TokenType.TRUE || sourceToken.type() == TokenType.FALSE;
            return new ConstantNode(sourceToken.pos(), ConstantType.Boolean, value);
        }

        public static ConstantNode ofNil(Token sourceToken) {
            assert sourceToken.type() == TokenType.NIL;
            return ofNil(sourceToken.pos());
        }

        // =============== UNCHECKED, TOKEN-FREE VERSIONS ===============
        public static ConstantNode ofString(Position pos, String stVal) {
            return new ConstantNode(pos, ConstantType.String, stVal);
        }

        public static ConstantNode ofIdent(Position pos, String identName) {
            return new ConstantNode(pos, ConstantType.Ident, identName);
        }

        public static ConstantNode ofInteger(Position pos, long lVal) {
            return new ConstantNode(pos, ConstantType.Integer, lVal);
        }

        public static ConstantNode ofNil(Position pos) {
            return new ConstantNode(pos, ConstantType.Nil, null);
        }
        // ==============================================================

        public enum ConstantType {
            Ident,
            String,
            Integer,
            Double,
            Boolean,
            Nil
        }
    }

    public static final class TableConstructionNode extends IRElement {
        public final Tuple<IRElement, IRElement>[] keyVals;

        @SafeVarargs
        public TableConstructionNode(Position pos, Tuple<IRElement, IRElement>... keyVals) {
            super(pos);
            this.keyVals = keyVals;
        }
    }

    public static final class ForStepNode extends IRElement {
        public final LocalVariableAccessor controlVar;
        public final LocalVariableAccessor upperBound;
        public final LocalVariableAccessor step;

        public ForStepNode(Position pos, LocalVariableAccessor controlVar, LocalVariableAccessor upperBound,
                           LocalVariableAccessor step) {
            super(pos);
            controlVar.getVariable().setWritten(pos);
            this.controlVar = controlVar;
            this.upperBound = upperBound;
            this.step = step;
        }
    }

    private static final HashMap<Class<?>, Integer> NODE_IDS = new HashMap<>();

    public static int getElemId(Class<?> elemClazz) {
        return NODE_IDS.get(elemClazz);
    }

    public static int maxElemId() {
        // currently we have 20 classes
        return NODE_IDS.size() - 1;
    }

    {
        var NODE_IDS = new HashMap<Class<?>, Integer>();
        for (var innerCls : IRElement.class.getDeclaredClasses()) {
            if (!IRElement.class.isAssignableFrom(innerCls)) {
                // skip all non sub-classes
                continue;
            }
            NODE_IDS.put(innerCls, NODE_IDS.size());
        }
    }
}
