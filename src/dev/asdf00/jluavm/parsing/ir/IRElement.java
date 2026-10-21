package dev.asdf00.jluavm.parsing.ir;

import dev.asdf00.jluavm.parsing.container.LocalVariable.LocalVariableAccessor;
import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.SymbolTable;
import dev.asdf00.jluavm.parsing.container.Token;
import dev.asdf00.jluavm.parsing.container.TokenType;
import dev.asdf00.jluavm.utils.Tuple;

import java.util.Arrays;

public abstract class IRElement {
    public final Position pos;

    public IRElement(Position pos) {
        this.pos = pos;
    }

    public abstract void accept(Visitor v);


    public static final class FunctionDefNode extends IRElement {
        public boolean isChunk;
        public LocalVariableAccessor[] captures;
        public boolean hasVararg;
        public LocalVariableAccessor[] accessors;
        public IRElement body;

        public FunctionDefNode(Position pos, boolean isChunk, LocalVariableAccessor[] captures, boolean hasVararg,
                               LocalVariableAccessor[] accessors, IRElement body) {
            super(pos);
            this.isChunk = isChunk;
            this.captures = captures;
            this.hasVararg = hasVararg;
            this.accessors = accessors;
            this.body = body;
        }

        @Override
        public void accept(Visitor v) {
            v.visitFunctionDef(this);
        }
    }

    public static final class BlockNode extends IRElement {
        public boolean isLexicalBlock;
        public IRElement[] statements;

        public BlockNode(Position pos, boolean isLexicalBlock, IRElement... statements) {
            super(pos);
            this.isLexicalBlock = isLexicalBlock;
            this.statements = statements;
        }

        @Override
        public void accept(Visitor v) {
            v.visitBlock(this);
        }

        public static IRElement append(IRElement base, IRElement... additionalStats) {
            if (base instanceof BlockNode origBlock) {
                IRElement[] concatenated = Arrays.copyOf(origBlock.statements, origBlock.statements.length + additionalStats.length);
                System.arraycopy(additionalStats, 0, concatenated, origBlock.statements.length, additionalStats.length);
                return new BlockNode(base.pos, origBlock.isLexicalBlock, concatenated);
            } else {
                IRElement[] concatenated = new IRElement[1 + additionalStats.length];
                concatenated[0] = base;
                System.arraycopy(additionalStats, 0, concatenated, 1, additionalStats.length);
                return new BlockNode(base.pos, false, concatenated);
            }
        }
    }

    public static final class ReturnNode extends IRElement {
        public IRElement[] expressions;

        public ReturnNode(Position pos, IRElement... expressions) {
            super(pos);
            this.expressions = expressions;
        }

        @Override
        public void accept(Visitor v) {
            v.visitReturn(this);
        }
    }

    public static final class ErrorNode extends IRElement {
        public IRElement msg;

        public ErrorNode(Position pos, IRElement msg) {
            super(pos);
            this.msg = msg;
        }

        @Override
        public void accept(Visitor v) {
            v.visitError(this);
        }
    }

    public static final class NopNode extends IRElement {

        public NopNode(Position pos) {
            super(pos);
        }

        @Override
        public void accept(Visitor v) {
            v.visitNop(this);
        }
    }

    public static final class GotoNode extends IRElement {
        public SymbolTable.JumpLabel label;

        public GotoNode(Position pos, SymbolTable.JumpLabel label) {
            this.label = label;
        }

        @Override
        public void accept(Visitor v) {
            v.visitGoto(this);
        }
    }

    public static final class LabelNode extends IRElement {
        public SymbolTable.JumpLabel label;

        public LabelNode(Position pos, SymbolTable.JumpLabel label) {
            super(pos);
            this.label = label;
        }

        @Override
        public void accept(Visitor v) {
            v.visitLabel(this);
        }
    }

    public static final class WhileNode extends IRElement {
        public IRElement condition;
        public IRElement body;
        public SymbolTable.JumpLabel breakLabel;

        public WhileNode(Position pos, IRElement condition, IRElement body, SymbolTable.JumpLabel breakLabel) {
            super(pos);
            this.condition = condition;
            this.body = body;
            this.breakLabel = breakLabel;
        }

        @Override
        public void accept(Visitor v) {
            v.visitWhile(this);
        }
    }

    public static final class IfNode extends IRElement {
        public IRElement trueCondition;
        public IRElement trueBody;
        public IRElement falseBody;

        public IfNode(Position pos, IRElement trueCondition, IRElement trueBody, IRElement falseBody) {
            super(pos);
            this.trueCondition = trueCondition;
            this.trueBody = trueBody;
            this.falseBody = falseBody;
        }

        public IfNode(Position pos, IRElement trueCondition, IRElement trueBody) {
            this(pos, trueCondition, trueBody, null);
        }

        @Override
        public void accept(Visitor v) {
            v.visitIf(this);
        }
    }

    public static final class LocalDeclarationNode extends IRElement {
        public LocalVariableAccessor local;

        public LocalDeclarationNode(Position pos, LocalVariableAccessor local) {
            super(pos);
            this.local = local;
        }

        @Override
        public void accept(Visitor v) {
            v.visitDeclareLocal(this);
        }
    }

    public static final class AssignmentNode extends IRElement {
        public IRElement[] locations;
        public IRElement[] expressions;

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

        @Override
        public void accept(Visitor v) {
            v.visitAssignment(this);
        }
    }

    public static final class EnvAccessNode extends IRElement {

        public EnvAccessNode(Position pos) {
            super(pos);
        }

        @Override
        public void accept(Visitor v) {
            v.visitEnvAccess(this);
        }
    }

    public static final class AdjustNumberOfValuesNode extends IRElement {
        private final int requested;
        private final IRElement multivalue;

        public AdjustNumberOfValuesNode(Position pos, int requested, IRElement multivalue) {
            super(pos);
            this.requested = requested;
            this.multivalue = multivalue;
        }

        public AdjustNumberOfValuesNode(int requested, IRElement multivalue) {
            this(multivalue.pos, requested, multivalue);
        }

        @Override
        public void accept(Visitor v) {
            v.visitAdjustNumberOfValues(this);
        }
    }

    public static final class ToNumberOrFailNode extends IRElement {
        private final IRElement value;
        private final String failFormat;

        public ToNumberOrFailNode(Position pos, IRElement value, String failFormat) {
            super(pos);
            this.value = value;
            this.failFormat = failFormat;
        }

        @Override
        public void accept(Visitor v) {
            v.visitToNumberOrFail(this);
        }
    }

    public static final class FunctionCallNode extends IRElement {
        private final IRElement callable;
        private final IRElement[] parameters;

        public FunctionCallNode(Position pos, IRElement callable, IRElement... parameters) {
            super(pos);
            this.callable = callable;
            this.parameters = parameters;
        }

        @Override
        public void accept(Visitor v) {
            v.visitFunctionCall(this);
        }
    }

    public static final class OopCallNode extends IRElement {
        private final IRElement env;
        private final IRElement obj;
        private final IRElement key;
        private final IRElement[] parameters;

        public OopCallNode(Position pos, IRElement env, IRElement obj, IRElement key, IRElement... parameters) {
            super(pos);
            this.env = env;
            this.obj = obj;
            this.key = key;
            this.parameters = parameters;
        }

        @Override
        public void accept(Visitor v) {
            v.visitOopCall(this);
        }
    }

    public static final class IndexedAccessNode extends IRElement {
        private final IRElement obj;
        private final IRElement key;

        public IndexedAccessNode(Position pos, IRElement obj, IRElement key) {
            super(pos);
            this.obj = obj;
            this.key = key;
        }

        @Override
        public void accept(Visitor v) {
            v.visitIndexedAccess(this);
        }
    }

    public static final class LocalAccessNode extends IRElement {
        private final LocalVariableAccessor local;

        public LocalAccessNode(Position pos, LocalVariableAccessor local) {
            super(pos);
            this.local = local;
        }

        @Override
        public void accept(Visitor v) {
            v.visitLocalAccess(this);
        }
    }

    public static final class OrNode extends IRElement {
        private final IRElement left;
        private final IRElement right;

        public OrNode(Position pos, IRElement left, IRElement right) {
            super(pos);
            this.left = left;
            this.right = right;
        }

        @Override
        public void accept(Visitor v) {
            v.visitOr(this);
        }
    }

    public static final class AndNode extends IRElement {
        private final IRElement left;
        private final IRElement right;

        public AndNode(Position pos, IRElement left, IRElement right) {
            super(pos);
            this.left = left;
            this.right = right;
        }

        @Override
        public void accept(Visitor v) {
            v.visitAnd(this);
        }
    }

    public static final class NotNode extends IRElement {
        private final IRElement value;

        public NotNode(Position pos, IRElement value) {
            super(pos);
            this.value = value;
        }

        @Override
        public void accept(Visitor v) {
            v.visitNot(this);
        }
    }

    public static final class BinaryOperationNode extends IRElement {
        private final TokenType op;
        private final IRElement left;
        private final IRElement right;

        public BinaryOperationNode(Position pos, TokenType op, IRElement left, IRElement right) {
            super(pos);
            this.op = op;
            this.left = left;
            this.right = right;
        }

        @Override
        public void accept(Visitor v) {
            v.visitBinaryOperation(this);
        }
    }

    public static final class UnaryOperationNode extends IRElement {
        private final TokenType op;
        private final IRElement value;

        public UnaryOperationNode(Position pos, TokenType op, IRElement value) {
            super(pos);
            this.op = op;
            this.value = value;
        }

        @Override
        public void accept(Visitor v) {
            v.visitUnaryOperation(this);
        }
    }

    public static final class ConstantNode extends IRElement {
        private final Token tok;

        public ConstantNode(Position pos, Token tok) {
            super(pos);
            this.tok = tok;
        }

        @Override
        public void accept(Visitor v) {
            v.visitConstant(this);
        }

        public static ConstantNode of(Token tok) {
            return new ConstantNode(tok.pos(), tok);
        }

        public static ConstantNode ofString(Position pos, String name) {
            return of(new Token(TokenType.LITERAL_STRING, pos, name));
        }

        public static ConstantNode ofIdent(Position pos, String name) {
            return of(new Token(TokenType.IDENT, pos, name));
        }

        public static ConstantNode ofInteger(Position pos, long value) {
            return of(new Token(TokenType.NUMERAL, pos, String.valueOf(value), -1, value));
        }

        public static ConstantNode ofBoolean(Position pos, boolean value) {
            return of(new Token(value ? TokenType.TRUE : TokenType.FALSE, pos));
        }

        public static ConstantNode ofNil(Position pos) {
            return of(new Token(TokenType.NIL, pos));
        }
    }

    public static final class TableConstructionNode extends IRElement {
        private final Tuple<IRElement, IRElement>[] keyVals;

        public TableConstructionNode(Position pos, Tuple<IRElement, IRElement>... keyVals) {
            super(pos);
            this.keyVals = keyVals;
        }

        @Override
        public void accept(Visitor v) {
            v.visitTableConstruction(this);
        }
    }

    public static final class ForStepNode extends IRElement {
        private final LocalVariableAccessor controlVar;
        private final LocalVariableAccessor step;
        private final SymbolTable.JumpLabel breakLabel;

        public ForStepNode(Position pos, LocalVariableAccessor controlVar, LocalVariableAccessor step,
                           SymbolTable.JumpLabel breakLabel) {
            super(pos);
            this.controlVar = controlVar;
            this.step = step;
            this.breakLabel = breakLabel;
        }

        @Override
        public void accept(Visitor v) {
            v.visitForStep(this);
        }
    }
}