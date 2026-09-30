package dev.asdf00.jluavm.parsing.ir.ast;

import dev.asdf00.jluavm.parsing.LocalVariable.LocalVariableAccessor;
import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.container.Token;
import dev.asdf00.jluavm.parsing.container.TokenType;
import dev.asdf00.jluavm.utils.Tuple;

import java.util.Arrays;

public interface AST {
    Position pos();

    void accept(Visitor v);

    record FunctionDefNode(Position pos, boolean isChunk, LocalVariableAccessor[] captures, boolean hasVararg,
                           LocalVariableAccessor[] accessors, AST body) implements AST {
        @Override
        public void accept(Visitor v) {
            v.visitFunctionDef(this);
        }
    }

    record BlockNode(Position pos, boolean isLexicalBlock, AST... statements) implements AST {
        @Override
        public void accept(Visitor v) {
            v.visitBlock(this);
        }

        public static AST append(AST base, AST... additionalStats) {
            if (base instanceof BlockNode origBlock) {
                AST[] concatenated = Arrays.copyOf(origBlock.statements(), origBlock.statements().length + additionalStats.length);
                System.arraycopy(additionalStats, 0, concatenated, origBlock.statements().length, additionalStats.length);
                return new BlockNode(base.pos(), origBlock.isLexicalBlock, concatenated);
            } else {
                AST[] concatenated = new AST[1 + additionalStats.length];
                concatenated[0] = base;
                System.arraycopy(additionalStats, 0, concatenated, 1, additionalStats.length);
                return new BlockNode(base.pos(), false, concatenated);
            }
        }
    }

    record ReturnNode(Position pos, AST... expressions) implements AST {
        @Override
        public void accept(Visitor v) {
            v.visitReturn(this);
        }
    }

    record ErrorNode(Position pos, AST msg) implements AST {
        @Override
        public void accept(Visitor v) {
            v.visitError(this);
        }
    }

    record NopNode(Position pos) implements AST {
        @Override
        public void accept(Visitor v) {
            v.visitNop(this);
        }
    }

    record GotoNode(Position pos, AST.JumpDestinationNode label) implements AST {
        @Override
        public void accept(Visitor v) {
            v.visitGoto(this);
        }
    }

    record LabelNode(Position pos, AST.JumpDestinationNode label) implements AST {
        @Override
        public void accept(Visitor v) {
            v.visitLabel(this);
        }
    }

    record WhileNode(Position pos, AST condition, AST body, AST.JumpDestinationNode breakLabel) implements AST {
        @Override
        public void accept(Visitor v) {
            v.visitWhile(this);
        }
    }

    record IfNode(Position pos, AST trueCondition, AST trueBody, AST falseBody) implements AST {
        public IfNode(Position pos, AST trueCondition, AST trueBody) {
            this(pos, trueCondition, trueBody, null);
        }

        @Override
        public void accept(Visitor v) {
            v.visitIf(this);
        }
    }

    record LocalDeclarationNode(Position pos, LocalVariableAccessor local) implements AST {
        @Override
        public void accept(Visitor v) {
            v.visitDeclareLocal(this);
        }
    }

    record AssignmentNode(Position pos, AST[] locations, AST... expressions) implements AST {
        public AssignmentNode {
            for (int i = 0; i < locations.length; i++) {
                if (locations[i] instanceof LocalAccessNode access) {
                    access.local.getVariable().setWritten(pos);
                }
            }
        }

        public AssignmentNode(Position pos, AST location, AST... expressions) {
            this(pos, new AST[]{location}, expressions);
        }

        @Override
        public void accept(Visitor v) {
            v.visitAssignment(this);
        }
    }

    record EnvAccessNode(Position pos) implements AST {
        @Override
        public void accept(Visitor v) {
            v.visitEnvAccess(this);
        }
    }

    record AdjustNumberOfValuesNode(Position pos, int requested, AST multivalue) implements AST {
        public AdjustNumberOfValuesNode(int requested, AST multivalue) {
            this(multivalue.pos(), requested, multivalue);
        }

        @Override
        public void accept(Visitor v) {
            v.visitAdjustNumberOfValues(this);
        }
    }

    record ToNumberOrFailNode(Position pos, AST value, String failFormat) implements AST {
        @Override
        public void accept(Visitor v) {
            v.visitToNumberOrFail(this);
        }
    }

    record FunctionCallNode(Position pos, AST callable, AST... parameters) implements AST {
        @Override
        public void accept(Visitor v) {
            v.visitFunctionCall(this);
        }
    }

    record OopCallNode(Position pos, AST env, AST obj, AST key, AST... parameters) implements AST {
        @Override
        public void accept(Visitor v) {
            v.visitOopCall(this);
        }
    }

    record IndexedAccessNode(Position pos, AST obj, AST key) implements AST {
        @Override
        public void accept(Visitor v) {
            v.visitIndexedAccess(this);
        }
    }

    record LocalAccessNode(Position pos, LocalVariableAccessor local) implements AST {
        @Override
        public void accept(Visitor v) {
            v.visitLocalAccess(this);
        }
    }

    record OrNode(Position pos, AST left, AST right) implements AST {
        @Override
        public void accept(Visitor v) {
            v.visitOr(this);
        }
    }

    record AndNode(Position pos, AST left, AST right) implements AST {
        @Override
        public void accept(Visitor v) {
            v.visitAnd(this);
        }
    }

    record NotNode(Position pos, AST value) implements AST {
        @Override
        public void accept(Visitor v) {
            v.visitNot(this);
        }
    }

    record BinaryOperationNode(Position pos, TokenType op, AST left, AST right) implements AST {
        @Override
        public void accept(Visitor v) {
            v.visitBinaryOperation(this);
        }
    }

    record UnaryOperationNode(Position pos, TokenType op, AST value) implements AST {
        @Override
        public void accept(Visitor v) {
            v.visitUnaryOperation(this);
        }
    }

    record ConstantNode(Position pos, Token tok) implements AST {
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

    record TableConstructionNode(Position pos, Tuple<AST, AST>... keyVals) implements AST {
        @Override
        public void accept(Visitor v) {
            v.visitTableConstruction(this);
        }
    }

    record JumpDestinationNode(Position pos) implements AST {
        @Override
        public void accept(Visitor v) {
            v.visitJumpDestination(this);
        }
    }

    record ForStepNode(Position pos, LocalVariableAccessor controlVar, LocalVariableAccessor step,
                       AST.JumpDestinationNode breakLabel) implements AST {
        @Override
        public void accept(Visitor v) {
            v.visitForStep(this);
        }
    }
}
