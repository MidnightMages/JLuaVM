package dev.asdf00.jluavm.parsing.ir;

import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.ir.ast.AST;
import dev.asdf00.jluavm.parsing.ir.ast.Visitor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;

public class CfgEmitter {
    public record CfgResult(CfgNode rootFunction, CfgNode[] additionalNestedFunctions) {
    }

    private final AST.FunctionDefNode ast;

    public CfgEmitter(AST.FunctionDefNode ast) {
        this.ast = ast;
    }

    public CfgResult emitCfg() {
        var visitor = new CfgVisitor(null);
        ast.body().accept(visitor);
        var rootCfg = visitor.finalize$();
        var extraCfgs = new LinkedHashSet<CfgNode>();
        var toVisit = new ArrayDeque<>(visitor.nestedCfgs);
        while (!toVisit.isEmpty()) {
            var item = toVisit.removeFirst();
            extraCfgs.add(item.finalize$());
            toVisit.addAll(item.nestedCfgs);
        }

        return new CfgResult(rootCfg, extraCfgs.toArray(CfgNode[]::new)); // todo also wrap CfgNodes into a function node to store some extra info
    }

    static class CfgVisitor implements Visitor {
        private final CfgVisitor rootVisitor;

        private record GotoToResolve(CfgVisitor NodeGotoSitsIn, AST.JumpDestinationNode destinationNode) {
        }

        public ArrayList<CfgVisitor> nestedCfgs = new ArrayList<>();
        public ArrayList<AST> statements = new ArrayList<>();
        public CfgVisitor defaultSuccessor = null;
        public AST ifCondition = null;
        public CfgVisitor branchingSuccessor = null;
        public CfgNode cfgNodeOfThis = new CfgNode(statements);
        public CfgVisitor $this = this;

        private ArrayList<GotoToResolve> $rootVisitorOnly_gotosToResolveLater;
        private HashMap<Position, CfgVisitor> $rootVisitorOnly_jumpLabels;

        public CfgVisitor(CfgVisitor rootVisitor) {
            if (rootVisitor == null) {
                this.rootVisitor = this;
                $rootVisitorOnly_gotosToResolveLater = new ArrayList<>();
                $rootVisitorOnly_jumpLabels = new HashMap<>();
            } else {
                this.rootVisitor = rootVisitor;
            }
        }

        private CfgVisitor newNestedCfgVisitor() {
            return new CfgVisitor(this.rootVisitor);
        }


        /**
         * Ends the current cfg block and starts a fresh one to allow jumping to it. Also makes sure that this block is a simple block and thus has no
         * if condition or branch already set up. Additonally ensures that the current block does not have a regular successor.
         */
        private void addContinuationToFreshBlock() {
            assert $this.defaultSuccessor == null;
            assert $this.ifCondition == null;
            assert $this.branchingSuccessor == null;
            var continuationVisitor = newNestedCfgVisitor();
            $this.defaultSuccessor = continuationVisitor;
            $this = continuationVisitor;
        }

        private boolean isFinalized = false;

        public CfgNode finalize$() {
            if (!isFinalized) {
                isFinalized = true;
                if (defaultSuccessor != null)
                    cfgNodeOfThis.setDefaultSuccessor(defaultSuccessor.finalize$());

                if (ifCondition != null) {
                    cfgNodeOfThis.ifCondition = ifCondition;
                    assert branchingSuccessor != null;
                    cfgNodeOfThis.setBranchingSuccessor(branchingSuccessor.finalize$());
                }
            }

            if (rootVisitor == this) {
                for (var g : $rootVisitorOnly_gotosToResolveLater) {
                    var destinationCfgNode = this.$rootVisitorOnly_jumpLabels.get(g.destinationNode().pos());
                    assert destinationCfgNode != null;
                    assert g.NodeGotoSitsIn.defaultSuccessor == null;
                    assert g.NodeGotoSitsIn.branchingSuccessor == null;
                    assert g.NodeGotoSitsIn.ifCondition == null;
                    g.NodeGotoSitsIn.cfgNodeOfThis.setDefaultSuccessor(destinationCfgNode.cfgNodeOfThis);
                }
                $rootVisitorOnly_gotosToResolveLater.clear();
            }

            return cfgNodeOfThis;
        }

        @Override
        public void visitFunctionDef(AST.FunctionDefNode n) {
            var nestedCfg = newNestedCfgVisitor();
            n.body().accept(nestedCfg);
            nestedCfg.finalize$();
            nestedCfgs.add(nestedCfg);
        }

        @Override
        public void visitBlock(AST.BlockNode n) {
            for (var stat : n.statements())
                stat.accept($this);
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
            assert $this.defaultSuccessor == null;
            assert $this.ifCondition == null;
            assert $this.branchingSuccessor == null;
            $rootVisitorOnly_gotosToResolveLater.add(new GotoToResolve($this, n.label())); // postpone the setting of '$this.defaultSuccessor'
        }

        @Override
        public void visitLabel(AST.LabelNode n) {
            addContinuationToFreshBlock();
            rootVisitor.$rootVisitorOnly_jumpLabels.put(n.label().pos(), $this);
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


            var successorVisitor = newNestedCfgVisitor();
            $this.ifCondition = n.trueCondition();
            var trueVisitor = newNestedCfgVisitor();
            var falseVisitor = newNestedCfgVisitor();
            $this.branchingSuccessor = trueVisitor;
            trueVisitor.defaultSuccessor = successorVisitor;

            $this.defaultSuccessor = falseVisitor;
            falseVisitor.defaultSuccessor = successorVisitor;

            if (n.trueBody() != null) {
                n.trueBody().accept(trueVisitor);
            }
            if (n.falseBody() != null)
                n.falseBody().accept(falseVisitor);

            $this = successorVisitor;
        }

        @Override
        public void visitWhile(AST.WhileNode n) {
            addContinuationToFreshBlock(); // jumps should go to $this
            var loopStartVisitor = $this;
            loopStartVisitor.ifCondition = n.condition(); // entry condition
            var loopBodyVisitor = newNestedCfgVisitor();
            loopStartVisitor.branchingSuccessor = loopBodyVisitor;
            n.body().accept(loopBodyVisitor);

            var successorVisitor = newNestedCfgVisitor();
            assert loopStartVisitor.defaultSuccessor == null;
            loopStartVisitor.defaultSuccessor = successorVisitor;
            $this = successorVisitor;
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
        public void visitAdjustNumberOfValues(AST.AdjustNumberOfValuesNode n) {
            statements.add(n);
        }

        @Override
        public void visitForStep(AST.ForStepNode n) {
            statements.add(n);
        }
    }
}
