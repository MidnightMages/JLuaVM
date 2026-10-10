package dev.asdf00.jluavm.parsing.ir;

import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.ir.ast.AST;

import java.util.ArrayList;
import java.util.function.Consumer;

public class CfgEmitter {
    private final AST.FunctionDefNode ast;

    public CfgEmitter(AST.FunctionDefNode ast) {
        this.ast = ast;
    }

    public CfgNode emitCfg() {
        return emitSingleCfgBlock(ast);
    }

    private static AST.BlockNode wrapIntoBlock(AST... ast) {
        return new AST.BlockNode(Position.UNKNOWN, false, ast);
    }

    private static CfgNode emitSingleCfgBlock(AST ast) {
        if (ast instanceof AST.BlockNode block) {
            final var prefixStats = new ArrayList<AST>();
            var cfgFixupActions = new ArrayList<Consumer<CfgNode>>();
            @SuppressWarnings("unchecked")
            Consumer<AST.BlockNode>[] processBlockRef = (Consumer<AST.BlockNode>[]) new Consumer[1];
            Consumer<AST.BlockNode> processBlock = root -> {
                for (var stat : root.statements()) { // dig through this set of statements until we find some nonlinear control flow
                    if (stat instanceof AST.BlockNode n) {
                        processBlockRef[0].accept(n);
                    } else if (stat instanceof AST.IfNode n) {
                        var ifCfg = emitSingleCfgBlock(n);
                        cfgFixupActions.add(cfg -> cfg.setDefaultSuccessor(ifCfg));
                    } else if (stat instanceof AST.WhileNode n) {
                        throw new UnsupportedOperationException("not implemented");
                    } else {
                        prefixStats.add(stat);
                    }
                }
            };
            processBlockRef[0] = processBlock;
            processBlock.accept(block);
            var rv = new CfgNode(new AST.BlockNode(block.pos(), false, prefixStats.toArray(AST[]::new)), null, null, null);
            for (var fixup : cfgFixupActions)
                fixup.accept(rv);
            return rv;
        } else if (ast instanceof AST.IfNode ifNode) {
            var falseBranch = ifNode.falseBody() != null ? emitSingleCfgBlock(wrapIntoBlock(ifNode.falseBody())) : null;
            var trueBranch = ifNode.trueBody() != null ? emitSingleCfgBlock(wrapIntoBlock(ifNode.trueBody())) : null;
            return new CfgNode(wrapIntoBlock(), falseBranch, ifNode.trueCondition(), trueBranch);
        } else if (ast instanceof AST.FunctionDefNode fdNode) {
            return emitSingleCfgBlock(fdNode.body());
        } else {
            throw new UnsupportedOperationException("missing case??");
        }
    }
}
