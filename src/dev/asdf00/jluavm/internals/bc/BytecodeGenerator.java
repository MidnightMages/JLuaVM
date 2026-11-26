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
                var n = (OrNode) elem;
                processNode(n.left); // compute left hand side value; if truthy, we are done; if falsy discard it, and return right hand side value

                int jpmIdx = bcIdx;
                bytecode[bcIdx++] = BytecodeOperations.BRANCH_IF_FALSY_PEEK; // branch if truthy, do not consume element
                {
                    bytecode[bcIdx++] = BytecodeOperations.STACK_POP;
                    processNode(n.right);
                }
                constants[jpmIdx] = bcIdx;
            }
            case 13 -> {
                assert elem.getClass() == AndNode.class;
                var n = (AndNode) elem;
                processNode(n.left); // compute left hand side value; if falsy, we are done; if truthy discard it, and return right hand side value

                int jpmIdx = bcIdx;
                bytecode[bcIdx++] = BytecodeOperations.BRANCH_IF_TRUTHY_PEEK; // branch if truthy, do not consume element
                {
                    bytecode[bcIdx++] = BytecodeOperations.STACK_POP;
                    processNode(n.right);
                }
                constants[jpmIdx] = bcIdx;
            }
            case 14 -> {
                assert elem.getClass() == NotNode.class;
                var n = (NotNode) elem;
                processNode(n.value);
                bytecode[bcIdx++] = BytecodeOperations.LUA_NOT;
            }
            case 15 -> {
                assert elem.getClass() == BinaryOperationNode.class;
                var n = (BinaryOperationNode) elem;
                switch (n.op) {
                    case DDOT, LT, LE, EQ, BOR, BXOR, BAND, SHL, SHR, ADD, SUB, MULT, DIV, FDIV, MOD, EXPONENT -> {
                        processNode(n.left);
                        processNode(n.right);
                        bytecode[bcIdx++] = switch (n.op) {
                            case DDOT -> BytecodeOperations.DDOT;
                            case LT -> BytecodeOperations.LT;
                            case LE -> BytecodeOperations.LE;
                            case EQ -> BytecodeOperations.EQ;
                            case BOR -> BytecodeOperations.BOR;
                            case BXOR -> BytecodeOperations.BXOR;
                            case BAND -> BytecodeOperations.BAND;
                            case SHL -> BytecodeOperations.SHL;
                            case SHR -> BytecodeOperations.SHR;
                            case ADD -> BytecodeOperations.ADD;
                            case SUB -> BytecodeOperations.SUB;
                            case MULT -> BytecodeOperations.MULT;
                            case DIV -> BytecodeOperations.DIV;
                            case FDIV -> BytecodeOperations.FDIV;
                            case MOD -> BytecodeOperations.MOD;
                            case EXPONENT -> BytecodeOperations.EXPONENT;
                            default -> throw new IllegalStateException("Unexpected value: %s".formatted(n.op));
                        };
                    }
                    // TODO should be able to flatten this double-switch into one by just removing the outer one at the cost of failing less fast
                    default -> throw new IllegalStateException("Binary operation %s is not implemented?".formatted(n.op));
                }
            }
            case 16 -> {
                assert elem.getClass() == UnaryOperationNode.class;
                var n = (UnaryOperationNode) elem;
                switch (n.op) {
                    case HASH, SUB, BXOR -> {
                        processNode(n.value);
                        bytecode[bcIdx++] = switch (n.op) {
                            case HASH -> BytecodeOperations.UNARY_HASH;
                            case SUB -> BytecodeOperations.UNARY_SUB;
                            case BXOR -> BytecodeOperations.UNARY_BITWISE_NEGATE;
                            default -> throw new IllegalStateException("Unexpected value: %s".formatted(n.op));
                        };
                    }
                    // TODO should be able to flatten this double-switch into one by just removing the outer one at the cost of failing less fast
                    default -> throw new IllegalStateException("Binary operation %s is not implemented?".formatted(n.op));
                }

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
            default -> throw new InternalLuaLoadingError("should not reach here: unknown IRElement class " + elem.getClass().getName());
        }
    }

    public final static class BytecodeOperations {
        @SuppressWarnings("unused")
        public static final byte UNDEFINED = 0;

        // ==== POP 2 VALUES FROM STACK, COMPUTE, PUSH 1 VALUE
        public static final byte ADD = 1;
        public static final byte SUB = 2;
        public static final byte MULT = 3;
        public static final byte DIV = 4;
        public static final byte FDIV = 5;
        public static final byte MOD = 6;
        public static final byte EXPONENT = 7;
        public static final byte SHL = 8;
        public static final byte SHR = 9;
        public static final byte BOR = 10;
        public static final byte BXOR = 11;
        public static final byte BAND = 12;
        public static final byte DDOT = 13;
        public static final byte LT = 14;
        public static final byte LE = 15;
        public static final byte EQ = 16;
        // ===================================================

        // ==== POP 1 VALUE FROM STACK, COMPUTE, PUSH 1 VALUE
        /**
         * Pop the current stack element, evaluate `toPush = isTruthy(popped) ? LuaFalse : LuaTrue`, push toPush
         */
        public static final byte LUA_NOT = 17;
        public static final byte UNARY_HASH = 18;
        public static final byte UNARY_SUB = 19;
        public static final byte UNARY_BITWISE_NEGATE = 20;
        // ==================================================

        /**
         * Peek at the current stack element and jump to the bytecode position stored in {@link #constants} at the current position iff the peeked element
         * is truthy, that is neither LuaNil nor LuaFalse. This operation does *not* modify the stack, at all.
         */
        public static final byte BRANCH_IF_TRUTHY_PEEK = 21;
        public static final byte BRANCH_IF_FALSY_PEEK = 23;

        /**
         * Pop and discard the current stack element
         */
        public static final byte STACK_POP = 22;
    }
}
