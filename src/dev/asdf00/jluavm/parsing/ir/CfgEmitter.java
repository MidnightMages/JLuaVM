package dev.asdf00.jluavm.parsing.ir;

import dev.asdf00.jluavm.parsing.ir.ast.AST;
import dev.asdf00.jluavm.parsing.ir.ast.Visitor;

import java.util.ArrayList;

public class CfgEmitter {
    private final AST.FunctionDefNode ast;

    public CfgEmitter(AST.FunctionDefNode ast) {
        this.ast = ast;
    }

    public CfgNode emitCfg() {
        var visitor = new CfgVisitor();
        ast.body().accept(visitor);
        return visitor.finalize$();
    }

    static class CfgVisitor implements Visitor {
        public ArrayList<AST> statements = new ArrayList<>();
        public CfgVisitor defaultSuccessor = null;
        public AST ifCondition = null;
        public CfgVisitor branchingSuccessor = null;
        public CfgNode cfgNodeOfThis = new CfgNode(statements);
        public CfgVisitor currentActualVisitor = this;

        private boolean isFinalized = false;

        public CfgNode finalize$() {
            if (isFinalized)
                return cfgNodeOfThis;

            isFinalized = true;
            if (defaultSuccessor != null)
                cfgNodeOfThis.setDefaultSuccessor(defaultSuccessor.finalize$());

            if (ifCondition != null) {
                cfgNodeOfThis.ifCondition = ifCondition;
                assert branchingSuccessor != null;
                cfgNodeOfThis.setBranchingSuccessor(branchingSuccessor.finalize$());
            }
            return cfgNodeOfThis;
        }

        @Override
        public void visitFunctionDef(AST.FunctionDefNode n) {
            var nestedCfg = new CfgVisitor();
            n.body().accept(nestedCfg);
            nestedCfg.finalize$();
            // todo link and return it somehow
        }

        @Override
        public void visitBlock(AST.BlockNode n) {
            for (var stat : n.statements())
                stat.accept(currentActualVisitor);
        }

        @Override
        public void visitReturn(AST.ReturnNode n) {
            statements.add(n);
        }

        @Override
        public void visitError(AST.ErrorNode n) {
            statements.add(n);
        }

        @Override
        public void visitNop(AST.NopNode n) {
            // nothing to emit
        }

        @Override
        public void visitGoto(AST.GotoNode n) {
            // TODO fill in
        }

        @Override
        public void visitLabel(AST.LabelNode n) {
            // TODO fill in
        }

        @Override
        public void visitWhile(AST.WhileNode n) {
            // TODO fill in
        }

        @Override
        public void visitIf(AST.IfNode n) {
            // represents an end to this CFG block. So write down the condition, traverse children, cook continuation

            // schedule the if as follows
            // ====================================== THIS CFG NODE ======================================
            // -------------------------
            // | preceeding statements |
            // -----------+-------------
            //            |
            //            |
            //            +-------(if condition is true)------> [ true body cfg node ] (branchingSuccessor)
            //            |                                           |
            //            |                           ■===============|===================================
            //            |                          ||               |
            //            V                          ||               V
            // ------------------------------------  ||     --------------------------------------------
            // | false body cfg (defaultSuccessor)|  ||     | stuff after the if (=successor cfg node) |
            // -----------+------------------------  ||     --------------------------------------------
            // ===========|==========================■
            //            V
            // --------------------------------------------
            // | stuff after the if (=successor cfg node) |
            // --------------------------------------------


            var successorVisitor = new CfgVisitor();
            ifCondition = n.trueCondition();
            var trueVisitor = new CfgVisitor();
            var falseVisitor = new CfgVisitor();
            this.branchingSuccessor = trueVisitor;
            trueVisitor.defaultSuccessor = successorVisitor;

            this.defaultSuccessor = falseVisitor;
            falseVisitor.defaultSuccessor = successorVisitor;

            if (n.trueBody() != null) {
                n.trueBody().accept(trueVisitor);
            }
            if (n.falseBody() != null)
                n.falseBody().accept(falseVisitor);

            this.currentActualVisitor = successorVisitor;
        }

        @Override
        public void visitDeclareLocal(AST.LocalDeclarationNode n) {
            statements.add(n);
        }

        @Override
        public void visitAssignment(AST.AssignmentNode n) {
            statements.add(n);
        }

        @Override
        public void visitEnvAccess(AST.EnvAccessNode n) {
            statements.add(n);
        }

        @Override
        public void visitAdjustNumberOfValues(AST.AdjustNumberOfValuesNode n) {
            statements.add(n);
        }

        @Override
        public void visitToNumberOrFail(AST.ToNumberOrFailNode n) {
            statements.add(n);
        }

        @Override
        public void visitFunctionCall(AST.FunctionCallNode n) {
            statements.add(n);
        }

        @Override
        public void visitOopCall(AST.OopCallNode n) {
            statements.add(n);
        }

        @Override
        public void visitIndexedAccess(AST.IndexedAccessNode n) {
            statements.add(n);
        }

        @Override
        public void visitLocalAccess(AST.LocalAccessNode n) {
            statements.add(n);
        }

        @Override
        public void visitOr(AST.OrNode n) {
            statements.add(n);
        }

        @Override
        public void visitAnd(AST.AndNode n) {
            statements.add(n);
        }

        @Override
        public void visitNot(AST.NotNode n) {
            statements.add(n);
        }

        @Override
        public void visitBinaryOperation(AST.BinaryOperationNode n) {
            statements.add(n);
        }

        @Override
        public void visitUnaryOperation(AST.UnaryOperationNode n) {
            statements.add(n);
        }

        @Override
        public void visitConstant(AST.ConstantNode n) {
            statements.add(n);
        }

        @Override
        public void visitTableConstruction(AST.TableConstructionNode n) {
            statements.add(n);
        }

        @Override
        public void visitForStep(AST.ForStepNode n) {
            statements.add(n);
        }

        @Override
        public void visitJumpDestination(AST.JumpDestinationNode n) {
            statements.add(n);
        }
    }
}
