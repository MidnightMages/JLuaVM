package dev.asdf00.jluavm.parsing.ir;

import dev.asdf00.jluavm.parsing.container.LocalVariable.LocalVariableAccessor;
import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.runtime.utils.Singletons;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Last element of every {@link BasicBlock}, describing where control flow continues.
 * <p>
 * Successor fields must only be changed through {@link BasicBlock#retarget(BasicBlock, BasicBlock)} so that the
 * predecessor lists of all involved blocks stay consistent.
 * <p>
 * Leaving the scope of to-be-closed variables is only possible via {@link Jump} and {@link Return}, which carry the
 * variables to close on that edge. A {@link Branch} never leaves such a scope.
 */
public sealed interface Terminator {

    List<BasicBlock> successors();

    /**
     * Replaces all occurrences of {@code old} as successor with {@code nu} without touching any predecessor lists.
     *
     * @return the number of replaced successor references
     */
    int replaceSuccessor(BasicBlock old, BasicBlock nu);

    final class Jump implements Terminator {
        private BasicBlock target;
        /**
         * Variables to close when taking this jump, in closing order (i.e. reverse declaration order). Never
         * {@code null}.
         */
        public LocalVariableAccessor[] toClose;

        public Jump(BasicBlock target, LocalVariableAccessor[] toClose) {
            assert target != null : "jump without target";
            assert toClose != null : "null close list";
            this.target = target;
            this.toClose = toClose;
        }

        public Jump(BasicBlock target) {
            this(target, Singletons.EMPTY_LOCAL_ACCESSOR_ARRAY);
        }

        public BasicBlock target() {
            return target;
        }

        @Override
        public List<BasicBlock> successors() {
            return List.of(target);
        }

        @Override
        public int replaceSuccessor(BasicBlock old, BasicBlock nu) {
            if (target == old) {
                target = nu;
                return 1;
            }
            return 0;
        }

        @Override
        public String toString() {
            return "jump B" + target.id + closingSuffix(toClose);
        }
    }

    final class Branch implements Terminator {
        public final Position pos;
        public IRElement condition;
        private BasicBlock onTrue;
        private BasicBlock onFalse;

        public Branch(Position pos, IRElement condition, BasicBlock onTrue, BasicBlock onFalse) {
            assert condition != null && onTrue != null && onFalse != null : "incomplete branch";
            this.pos = pos;
            this.condition = condition;
            this.onTrue = onTrue;
            this.onFalse = onFalse;
        }

        public BasicBlock onTrue() {
            return onTrue;
        }

        public BasicBlock onFalse() {
            return onFalse;
        }

        @Override
        public List<BasicBlock> successors() {
            return List.of(onTrue, onFalse);
        }

        @Override
        public int replaceSuccessor(BasicBlock old, BasicBlock nu) {
            int cnt = 0;
            if (onTrue == old) {
                onTrue = nu;
                cnt++;
            }
            if (onFalse == old) {
                onFalse = nu;
                cnt++;
            }
            return cnt;
        }

        @Override
        public String toString() {
            return "branch %s ? B%d : B%d".formatted(condition.getClass().getSimpleName(), onTrue.id, onFalse.id);
        }
    }

    final class Return implements Terminator {
        public final Position pos;
        public IRElement[] values;
        /**
         * Variables to close after evaluating {@link #values}, in closing order. Never {@code null}.
         */
        public LocalVariableAccessor[] toClose;

        public Return(Position pos, IRElement[] values, LocalVariableAccessor[] toClose) {
            assert toClose != null : "null close list";
            this.pos = pos;
            this.values = values;
            this.toClose = toClose;
        }

        @Override
        public List<BasicBlock> successors() {
            return List.of();
        }

        @Override
        public int replaceSuccessor(BasicBlock old, BasicBlock nu) {
            return 0;
        }

        @Override
        public String toString() {
            return "return %d value(s)%s".formatted(values.length, closingSuffix(toClose));
        }
    }

    private static String closingSuffix(LocalVariableAccessor[] toClose) {
        return toClose.length == 0 ? "" : Arrays.stream(toClose).map(a -> a.getVariable().name)
                .collect(Collectors.joining(", ", " closing [", "]"));
    }
}
