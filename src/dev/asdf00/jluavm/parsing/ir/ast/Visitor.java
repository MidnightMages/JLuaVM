package dev.asdf00.jluavm.parsing.ir.ast;

import dev.asdf00.jluavm.parsing.ir.ast.AST.*;

public interface Visitor {
    default void visitFunctionDef(FunctionDefNode functionDefNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitBlock(BlockNode blockNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitReturn(ReturnNode returnNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitError(ErrorNode errorNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitNop(NopNode nopNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitGoto(GotoNode gotoNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitLabel(LabelNode labelNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitWhile(WhileNode whileNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitIf(IfNode ifNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitDeclareLocal(LocalDeclarationNode localDeclarationNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitAssignment(AssignmentNode assignmentNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitEnvAccess(EnvAccessNode envAccessNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitAdjustNumberOfValues(AdjustNumberOfValuesNode pruneToOneValueNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitToNumberOrFail(ToNumberOrFailNode toNumberOrFailNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitFunctionCall(FunctionCallNode functionCallNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitOopCall(OopCallNode oopCallNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitIndexedAccess(IndexedAccessNode indexedAccessNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitLocalAccess(LocalAccessNode localAccessNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitOr(OrNode orNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitAnd(AndNode andNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitNot(NotNode notNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitBinaryOperation(BinaryOperationNode binaryOperationNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitUnaryOperation(UnaryOperationNode unaryOperationNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitConstant(ConstantNode constantNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitTableConstruction(TableConstructionNode tableConstructionNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitForStep(ForStepNode forStepNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }

    default void visitJumpDestination(JumpDestinationNode jumpDestinationNode) {
        throw new IllegalStateException("visit operation has not been defined");
    }
}