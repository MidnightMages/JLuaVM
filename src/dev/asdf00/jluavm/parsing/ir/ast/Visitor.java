package dev.asdf00.jluavm.parsing.ir.ast;

import dev.asdf00.jluavm.parsing.ir.ast.AST.*;

public interface Visitor {
    void visitFunctionDef(FunctionDefNode functionDefNode);

    void visitBlock(BlockNode blockNode);

    void visitReturn(ReturnNode returnNode);

    void visitError(ErrorNode errorNode);

    void visitNop(NopNode nopNode);

    void visitGoto(GotoNode gotoNode);

    void visitLabel(LabelNode labelNode);

    void visitWhile(WhileNode whileNode);

    void visitIf(IfNode ifNode);

    void visitDeclareLocal(LocalDeclarationNode localDeclarationNode);

    void visitAssignment(AssignmentNode assignmentNode);

    void visitEnvAccess(EnvAccessNode envAccessNode);

    void visitAdjustNumberOfValues(AdjustNumberOfValuesNode pruneToOneValueNode);

    void visitToNumberOrFail(ToNumberOrFailNode toNumberOrFailNode);

    void visitFunctionCall(FunctionCallNode functionCallNode);

    void visitOopCall(OopCallNode oopCallNode);

    void visitIndexedAccess(IndexedAccessNode indexedAccessNode);

    void visitLocalAccess(LocalAccessNode localAccessNode);

    void visitOr(OrNode orNode);

    void visitAnd(AndNode andNode);

    void visitNot(NotNode notNode);

    void visitBinaryOperation(BinaryOperationNode binaryOperationNode);

    void visitUnaryOperation(UnaryOperationNode unaryOperationNode);

    void visitConstant(ConstantNode constantNode);

    void visitTableConstruction(TableConstructionNode tableConstructionNode);

    void visitForStep(ForStepNode forStepNode);

    void visitJumpDestination(JumpDestinationNode jumpDestinationNode);
}