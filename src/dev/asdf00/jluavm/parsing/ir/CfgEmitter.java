package dev.asdf00.jluavm.parsing.ir;

import dev.asdf00.jluavm.parsing.ir.ast.AST;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class CfgEmitter {
    public record CfgResult(CfgNode rootFunction, CfgNode[] additionalNestedFunctions) {
    }

    public record CfgNodePair(CfgNode head, CfgNode tail, CfgNode[] additionalNestedFunctions) {
    }

    private final AST.FunctionDefNode ast;

    public CfgEmitter(AST.FunctionDefNode ast) {
        this.ast = ast;
    }

    private record CfgScope(CfgNode cfgNode, ArrayDeque<AST> nodesToProcess) {
    }

    public CfgResult emitCfg() {
        var trackedCfgRoots = new ArrayList<CfgNode>();
        var scopesToProcess = new Stack<CfgScope>();
        scopesToProcess.push(new CfgScope(new CfgNode(), new ArrayDeque<>(List.of(ast.body()))));

        while (!scopesToProcess.isEmpty()) {
            var currScope = scopesToProcess.peek();
            var currNodeQueue = currScope.nodesToProcess;
            if (currNodeQueue.isEmpty()) { // we are done processing the current scope, return the the previous one
                scopesToProcess.pop();
                continue;
            }

            var rawNode = currNodeQueue.removeFirst();
            var currCfgNode = currScope.cfgNode;
            switch (rawNode) {
                case AST.FunctionDefNode n -> {
                    var newRoot = new CfgNode();
                    scopesToProcess.push(new CfgScope(newRoot, new ArrayDeque<>(List.of(n.body()))));
                    trackedCfgRoots.add(newRoot);
                    // TODO
                }
                case AST.BlockNode n -> {
                    var newRoot = new CfgNode();
                    scopesToProcess.push(new CfgScope(newRoot, new ArrayDeque<>(List.of(n.statements()))));
                    trackedCfgRoots.add(newRoot);
                }
                case AST.ReturnNode n -> {
                    currCfgNode.statements.add(n);
                }
                case AST.ErrorNode n -> {
                    currCfgNode.statements.add(n);
                }
                case AST.GotoNode n -> {
                }
                case AST.LabelNode n -> {
                }
                case AST.IfNode n -> {
                }
                case AST.WhileNode n -> {
                }
                case AST.LocalDeclarationNode n -> {
                    currCfgNode.statements.add(n);
                }
                case AST.AssignmentNode n -> {
                    currCfgNode.statements.add(n);
                }
                case AST.AdjustNumberOfValuesNode n -> {
                    currCfgNode.statements.add(n);
                }
                case AST.ForStepNode n -> {
                }
                case AST.NopNode n -> {
                }
                default -> throw new IllegalStateException("Unexpected value: %s".formatted(rawNode));
            }
        }

        return new CfgResult(trackedCfgRoots.getFirst(), trackedCfgRoots.stream().skip(1).toArray(CfgNode[]::new));
    }

//    private static CfgNodePair createCfgFromAst(AST.FunctionDefNode defNode) {
//        var head = new CfgNode();
//        var tail = head;
//
//        var statements = new ArrayList<AST>();
//        switch (defNode.body()) {
//            case AST.BlockNode b -> statements.addAll(Arrays.stream(b.statements()).toList());
//            case null -> throw new IllegalStateException("shouldnt be null");
//            default -> statements.add(defNode.body());
//        }
//
//        var flattened = flattenBlocksRecursive(statements);
//
//        for (var stat : flattened) {
//            switch (stat) {
//                case AST.FunctionDefNode n -> {
//                }
//                case AST.ReturnNode n -> {
//                }
//                case AST.ErrorNode n -> {
//                }
//                case AST.NopNode n -> {
//                }
//                case AST.GotoNode n -> {
//                }
//                case AST.LabelNode n -> {
//                }
//                case AST.IfNode n -> {
//                }
//                case AST.WhileNode n -> {
//                }
//                case AST.LocalDeclarationNode n -> {
//                }
//                case AST.AssignmentNode n -> {
//                }
//                case AST.AdjustNumberOfValuesNode n -> {
//                }
//                case AST.ForStepNode n -> {
//                }
//                case AST.BlockNode n -> throw new IllegalStateException("shouldnt be hit due to flattening");
//                default -> throw new IllegalStateException("Unexpected value: " + stat);
//            }
//        }
//
//        return new CfgResult(rootNode, new CfgNode[0]);
//    }
//
//    private static ArrayList<AST> flattenBlocksRecursive(ArrayList<AST> stats) {
//        boolean anyFlattened;
//        do { // keep unpacking all block nodes
//            anyFlattened = false;
//            var next = new ArrayList<AST>();
//            for (var stat : stats) {
//                if (stat instanceof AST.BlockNode block) {
//                    next.addAll(List.of(block.statements()));
//                    anyFlattened = true;
//                } else {
//                    next.add(stat);
//                }
//            }
//            stats = next;
//        } while (anyFlattened);
//        return stats;
//    }

//    private static CfgResult createCfgFromStatementsRecursive(ArrayList<AST> statements) {
//        var visitor = new CfgVisitor();
//        var currentNode = new CfgNode();
//
//        var statements = new ArrayList<AST>();
//        switch (defNode.body()) {
//            case AST.BlockNode b -> statements.addAll(Arrays.stream(b.statements()).toList());
//            case null -> throw new IllegalStateException("shouldnt be null");
//            default -> statements.add(defNode.body());
//        }
//
//        for (var stat : statements) {
//            switch (stat) {
//                case AST.BlockNode n ->
//                default -> throw new IllegalStateException("Unexpected value: " + stat);
//            }
//        }
//    }
//
//    static class CfgVisitor implements Visitor {
//        public ArrayList<AST> statements;
//        public ArrayList<AST> statements;
//
//        @Override
//        public void visitFunctionDef(AST.FunctionDefNode n) {
//            var nestedCfg = newNestedCfgVisitor();
//            n.body().accept(nestedCfg);
//            nestedCfg.finalize$();
//            nestedCfgs.add(nestedCfg);
//        }
//
//        @Override
//        public void visitBlock(AST.BlockNode n) {
//            for (var stat : n.statements())
//                stat.accept($this);
//        }
//
//        @Override
//        public void visitReturn(AST.ReturnNode n) {
//            statements.add(n);
//        }
//
//        @Override
//        public void visitError(AST.ErrorNode n) {
//            statements.add(n);
//        }
//
//        @Override
//        public void visitNop(AST.NopNode n) {
//            // nothing to emit
//        }
//
//        @Override
//        public void visitGoto(AST.GotoNode n) {
//            assert $this.defaultSuccessor == null;
//            assert $this.ifCondition == null;
//            assert $this.branchingSuccessor == null;
//            $rootVisitorOnly_gotosToResolveLater.add(new CfgVisitorOld.GotoToResolve($this, n.label())); // postpone the setting of '$this.defaultSuccessor'
//        }
//
//        @Override
//        public void visitLabel(AST.LabelNode n) {
//            addContinuationToFreshBlock();
//            rootVisitor.$rootVisitorOnly_jumpLabels.put(n.label().pos(), $this);
//        }
//
//        @Override
//        public void visitIf(AST.IfNode n) {
//            // represents an end to this CFG block. So write down the condition, traverse children, cook continuation
//
//            // schedule the if as follows
//            // ====================================== THIS CFG NODE ======================================
//            // -------------------------
//            // | preceeding statements |
//            // -----------+-------------
//            //            |
//            //            |
//            //            +-------(if condition is true)------> [ true body cfg node ] (branchingSuccessor)
//            //            |                                           |
//            //            |                           ■===============|===================================
//            //            |                          ||               |
//            //            V                          ||               V
//            // ------------------------------------  ||     --------------------------------------------
//            // | false body cfg (defaultSuccessor)|  ||     | stuff after the if (=successor cfg node) |
//            // -----------+------------------------  ||     --------------------------------------------
//            // ===========|==========================■
//            //            V
//            // --------------------------------------------
//            // | stuff after the if (=successor cfg node) |
//            // --------------------------------------------
//
//
//            var successorVisitor = newNestedCfgVisitor();
//            $this.ifCondition = n.trueCondition();
//            var trueVisitor = newNestedCfgVisitor();
//            var falseVisitor = newNestedCfgVisitor();
//            $this.branchingSuccessor = trueVisitor;
//            trueVisitor.defaultSuccessor = successorVisitor;
//
//            $this.defaultSuccessor = falseVisitor;
//            falseVisitor.defaultSuccessor = successorVisitor;
//
//            if (n.trueBody() != null) {
//                n.trueBody().accept(trueVisitor);
//            }
//            if (n.falseBody() != null)
//                n.falseBody().accept(falseVisitor);
//
//            $this = successorVisitor;
//        }
//
//        @Override
//        public void visitWhile(AST.WhileNode n) {
//            addContinuationToFreshBlock(); // jumps should go to $this
//            var loopStartVisitor = $this;
//            loopStartVisitor.ifCondition = n.condition(); // entry condition
//            var loopBodyVisitor = newNestedCfgVisitor();
//            loopStartVisitor.branchingSuccessor = loopBodyVisitor;
//            n.body().accept(loopBodyVisitor);
//
//            var successorVisitor = newNestedCfgVisitor();
//            assert loopStartVisitor.defaultSuccessor == null;
//            loopStartVisitor.defaultSuccessor = successorVisitor;
//            $this = successorVisitor;
//        }
//
//        @Override
//        public void visitDeclareLocal(AST.LocalDeclarationNode n) {
//            statements.add(n);
//        }
//
//        @Override
//        public void visitAssignment(AST.AssignmentNode n) {
//            statements.add(n);
//        }
//
//        @Override
//        public void visitAdjustNumberOfValues(AST.AdjustNumberOfValuesNode n) {
//            statements.add(n);
//        }
//
//        @Override
//        public void visitForStep(AST.ForStepNode n) {
//            statements.add(n);
//        }
//    }
//
//    static class CfgVisitorOld implements Visitor {
//        private final CfgVisitorOld rootVisitor;
//
//        private record GotoToResolve(CfgVisitorOld NodeGotoSitsIn, AST.JumpDestinationNode destinationNode) {
//        }
//
//        public ArrayList<CfgVisitorOld> nestedCfgs = new ArrayList<>();
//        public ArrayList<AST> statements = new ArrayList<>();
//        public CfgVisitorOld defaultSuccessor = null;
//        public AST ifCondition = null;
//        public CfgVisitorOld branchingSuccessor = null;
//        public CfgNode cfgNodeOfThis = new CfgNode(statements);
//        public CfgVisitorOld $this = this;
//
//        private ArrayList<GotoToResolve> $rootVisitorOnly_gotosToResolveLater;
//        private HashMap<Position, CfgVisitorOld> $rootVisitorOnly_jumpLabels;
//
//        public CfgVisitorOld(CfgVisitorOld rootVisitor) {
//            if (rootVisitor == null) {
//                this.rootVisitor = this;
//                $rootVisitorOnly_gotosToResolveLater = new ArrayList<>();
//                $rootVisitorOnly_jumpLabels = new HashMap<>();
//            } else {
//                this.rootVisitor = rootVisitor;
//            }
//        }
//
//        private CfgVisitorOld newNestedCfgVisitor() {
//            return new CfgVisitorOld(this.rootVisitor);
//        }
//
//
//        /**
//         * Ends the current cfg block and starts a fresh one to allow jumping to it. Also makes sure that this block is a simple block and thus has no
//         * if condition or branch already set up. Additonally ensures that the current block does not have a regular successor.
//         */
//        private void addContinuationToFreshBlock() {
//            assert $this.defaultSuccessor == null;
//            assert $this.ifCondition == null;
//            assert $this.branchingSuccessor == null;
//            var continuationVisitor = newNestedCfgVisitor();
//            $this.defaultSuccessor = continuationVisitor;
//            $this = continuationVisitor;
//        }
//
//        private boolean isFinalized = false;
//
//        public CfgNode finalize$() {
//            if (!isFinalized) {
//                isFinalized = true;
//                if (defaultSuccessor != null)
//                    cfgNodeOfThis.setDefaultSuccessor(defaultSuccessor.finalize$());
//
//                if (ifCondition != null) {
//                    cfgNodeOfThis.ifCondition = ifCondition;
//                    assert branchingSuccessor != null;
//                    cfgNodeOfThis.setBranchingSuccessor(branchingSuccessor.finalize$());
//                }
//            }
//
//            if (rootVisitor == this) {
//                for (var g : $rootVisitorOnly_gotosToResolveLater) {
//                    var destinationCfgNode = this.$rootVisitorOnly_jumpLabels.get(g.destinationNode().pos());
//                    assert destinationCfgNode != null;
//                    assert g.NodeGotoSitsIn.defaultSuccessor == null;
//                    assert g.NodeGotoSitsIn.branchingSuccessor == null;
//                    assert g.NodeGotoSitsIn.ifCondition == null;
//                    g.NodeGotoSitsIn.cfgNodeOfThis.setDefaultSuccessor(destinationCfgNode.cfgNodeOfThis);
//                }
//                $rootVisitorOnly_gotosToResolveLater.clear();
//            }
//
//            return cfgNodeOfThis;
//        }
//
//        @Override
//        public void visitFunctionDef(AST.FunctionDefNode n) {
//            var nestedCfg = newNestedCfgVisitor();
//            n.body().accept(nestedCfg);
//            nestedCfg.finalize$();
//            nestedCfgs.add(nestedCfg);
//        }
//
//        @Override
//        public void visitBlock(AST.BlockNode n) {
//            for (var stat : n.statements())
//                stat.accept($this);
//        }
//
//        @Override
//        public void visitReturn(AST.ReturnNode n) {
//            statements.add(n);
//        }
//
//        @Override
//        public void visitError(AST.ErrorNode n) {
//            statements.add(n);
//        }
//
//        @Override
//        public void visitNop(AST.NopNode n) {
//            // nothing to emit
//        }
//
//        @Override
//        public void visitGoto(AST.GotoNode n) {
//            assert $this.defaultSuccessor == null;
//            assert $this.ifCondition == null;
//            assert $this.branchingSuccessor == null;
//            $rootVisitorOnly_gotosToResolveLater.add(new GotoToResolve($this, n.label())); // postpone the setting of '$this.defaultSuccessor'
//        }
//
//        @Override
//        public void visitLabel(AST.LabelNode n) {
//            addContinuationToFreshBlock();
//            rootVisitor.$rootVisitorOnly_jumpLabels.put(n.label().pos(), $this);
//        }
//
//        @Override
//        public void visitIf(AST.IfNode n) {
//            // represents an end to this CFG block. So write down the condition, traverse children, cook continuation
//
//            // schedule the if as follows
//            // ====================================== THIS CFG NODE ======================================
//            // -------------------------
//            // | preceeding statements |
//            // -----------+-------------
//            //            |
//            //            |
//            //            +-------(if condition is true)------> [ true body cfg node ] (branchingSuccessor)
//            //            |                                           |
//            //            |                           ■===============|===================================
//            //            |                          ||               |
//            //            V                          ||               V
//            // ------------------------------------  ||     --------------------------------------------
//            // | false body cfg (defaultSuccessor)|  ||     | stuff after the if (=successor cfg node) |
//            // -----------+------------------------  ||     --------------------------------------------
//            // ===========|==========================■
//            //            V
//            // --------------------------------------------
//            // | stuff after the if (=successor cfg node) |
//            // --------------------------------------------
//
//
//            var successorVisitor = newNestedCfgVisitor();
//            $this.ifCondition = n.trueCondition();
//            var trueVisitor = newNestedCfgVisitor();
//            var falseVisitor = newNestedCfgVisitor();
//            $this.branchingSuccessor = trueVisitor;
//            trueVisitor.defaultSuccessor = successorVisitor;
//
//            $this.defaultSuccessor = falseVisitor;
//            falseVisitor.defaultSuccessor = successorVisitor;
//
//            if (n.trueBody() != null) {
//                n.trueBody().accept(trueVisitor);
//            }
//            if (n.falseBody() != null)
//                n.falseBody().accept(falseVisitor);
//
//            $this = successorVisitor;
//        }
//
//        @Override
//        public void visitWhile(AST.WhileNode n) {
//            addContinuationToFreshBlock(); // jumps should go to $this
//            var loopStartVisitor = $this;
//            loopStartVisitor.ifCondition = n.condition(); // entry condition
//            var loopBodyVisitor = newNestedCfgVisitor();
//            loopStartVisitor.branchingSuccessor = loopBodyVisitor;
//            n.body().accept(loopBodyVisitor);
//
//            var successorVisitor = newNestedCfgVisitor();
//            assert loopStartVisitor.defaultSuccessor == null;
//            loopStartVisitor.defaultSuccessor = successorVisitor;
//            $this = successorVisitor;
//        }
//
//        @Override
//        public void visitDeclareLocal(AST.LocalDeclarationNode n) {
//            statements.add(n);
//        }
//
//        @Override
//        public void visitAssignment(AST.AssignmentNode n) {
//            statements.add(n);
//        }
//
//        @Override
//        public void visitAdjustNumberOfValues(AST.AdjustNumberOfValuesNode n) {
//            statements.add(n);
//        }
//
//        @Override
//        public void visitForStep(AST.ForStepNode n) {
//            statements.add(n);
//        }
//    }
}
