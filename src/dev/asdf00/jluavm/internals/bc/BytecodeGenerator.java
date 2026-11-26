package dev.asdf00.jluavm.internals.bc;

import dev.asdf00.jluavm.exceptions.loading.InternalLuaLoadingError;
import dev.asdf00.jluavm.parsing.ir.BasicBlock;
import dev.asdf00.jluavm.parsing.ir.ControlFlowGraph;
import dev.asdf00.jluavm.parsing.ir.IRElement;
import dev.asdf00.jluavm.parsing.ir.IRElement.*;

import java.util.BitSet;

public class BytecodeGenerator {
    private final ControlFlowGraph cfg;
    private final BitSet visited;

    int bcIdx;
    private short[] bytecode;
    private Object[] constants;

    public BytecodeGenerator(ControlFlowGraph cfg) {
        this.cfg = cfg;
        this.visited = new BitSet(cfg.maxBlockId());
    }

    private void generate() {
        BasicBlock curBlk = cfg.entry;

    }

    private void processBlock(BasicBlock blk) {
        // wait if predecessors are not yet visited to achieve reverse post-order
        for (var upPred : blk.predecessors) {
            if (!visited.get(upPred.id)) {
                return;
            }
        }
        visited.set(blk.id);

        // generate bytecode for each statement
        for (int i = 0; i < blk.statements.size(); i++) {
            var s = blk.statements.get(i);

        }
    }

    private void processNode(IRElement elem) {
        switch (IRElement.getElemId(elem.getClass())) {
            case 0 -> {
                assert elem.getClass() == ErrorNode.class;

            }
            case 1 -> {
                assert elem.getClass() == LocalDeclarationNode.class;

            }
            case 2 -> {
                assert elem.getClass() == AssignmentNode.class;

            }
            case 3 -> {
                assert elem.getClass() == CheckClosableNode.class;

            }
            case 4 -> {
                assert elem.getClass() == FunctionDefNode.class;

            }
            case 5 -> {
                assert elem.getClass() == EnvAccessNode.class;

            }
            case 6 -> {
                assert elem.getClass() == AdjustNumberOfValuesNode.class;

            }
            case 7 -> {
                assert elem.getClass() == ToNumberOrFailNode.class;

            }
            case 8 -> {
                assert elem.getClass() == FunctionCallNode.class;

            }
            case 9 -> {
                assert elem.getClass() == OopCallNode.class;

            }
            case 10 -> {
                assert elem.getClass() == IndexedAccessNode.class;

            }
            case 11 -> {
                assert elem.getClass() == LocalAccessNode.class;

            }
            case 12 -> {
                assert elem.getClass() == OrNode.class;

            }
            case 13 -> {
                assert elem.getClass() == AndNode.class;
                var n = (BinaryOperationNode) elem;
                processNode(n.left);
                int jpmIdx = bcIdx;
                bytecode[bcIdx++] = 22; // branch if falsy peeking op
                bytecode[bcIdx++] = 1; // pop op
                processNode(n.right);
                constants[jpmIdx] = bcIdx;
                bytecode[bcIdx++] = 23; // isTruthy op
            }
            case 14 -> {
                assert elem.getClass() == NotNode.class;

            }
            case 15 -> {
                assert elem.getClass() == BinaryOperationNode.class;
                var n = (BinaryOperationNode) elem;
                switch (((BinaryOperationNode) elem).op) {
                    case ADD -> {
                        processNode(n.left);
                        processNode(n.right);
                        bytecode[bcIdx++] = 24; // opcode for add
                    }
                }
            }
            case 16 -> {
                assert elem.getClass() == UnaryOperationNode.class;

            }
            case 17 -> {
                assert elem.getClass() == ConstantNode.class;

            }
            case 18 -> {
                assert elem.getClass() == TableConstructionNode.class;

            }
            case 19 -> {
                assert elem.getClass() == ForStepNode.class;

            }
            default ->
                    new InternalLuaLoadingError("should not reach here: unknown IRElement class " + elem.getClass().getName());
        }
    }

}
