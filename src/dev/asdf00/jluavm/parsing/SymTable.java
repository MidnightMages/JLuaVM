package dev.asdf00.jluavm.parsing;

import dev.asdf00.jluavm.exceptions.loading.LuaSemanticException;
import dev.asdf00.jluavm.parsing.container.*;
import dev.asdf00.jluavm.parsing.ir.controlflow.BreakNode;
import dev.asdf00.jluavm.parsing.ir.controlflow.GotoNode;
import dev.asdf00.jluavm.parsing.ir.controlflow.LabelNode;
import dev.asdf00.jluavm.utils.Quadruple;
import dev.asdf00.jluavm.utils.Triple;
import dev.asdf00.jluavm.utils.Tuple;

import java.util.*;

public class SymTable {
    private int idSupplier = 0;
    private VarScope rootScope = null;
    private VarScope curScope = null;
    private final HashMap<VarScope, Stack<BreakNode>> breaksToPatch = new HashMap<>();
    private final Stack<HashMap<String, ArrayList<Triple<GotoNode, Triple<Integer, Integer, Boolean>[], Position>>>> yetToFixGotos = new Stack<>();
    private final Stack<ArrayList<Tuple<GotoNode, Position>>> dangerZone = new Stack<>();

    public void enterPlainScope(boolean isInlined) {
        enterScope(false, false, false, isInlined);
    }

    public void enterLoopScope() {
        enterScope(false, false, true, false);
    }

    public void enterFunctionScope(boolean hasParamsArg) {
        enterScope(true, hasParamsArg, false, false);
    }

    private void enterScope(boolean isFunctionBorder, boolean hasParamsArg, boolean isLoop, boolean isInlined) {
        var prev = curScope;
        curScope = new VarScope(curScope, idSupplier++, isFunctionBorder, hasParamsArg, isLoop, isInlined);
        if (rootScope == null) {
            rootScope = curScope;
        }
        if (prev != null) {
            prev.children.add(curScope);
        }
        yetToFixGotos.push(new HashMap<>());
        dangerZone.push(new ArrayList<>());
    }

    public VarScope exitScope() {
        var rVal = curScope;
        curScope = curScope.parent;

        // make inner yet to fix gotos visible to later label definitions do final checks if we exit a function scope
        if (rVal.isFunctionBorder) {
            // function exit
            if (!yetToFixGotos.peek().isEmpty()) {
                throw new LuaSemanticException(yetToFixGotos.peek().values().stream().findAny().get().stream().findAny().get().z(), "no goto target found");
            }
            // remove inner map
            yetToFixGotos.pop();
        } else {
            // exit inner scope
            var inner = yetToFixGotos.pop();
            for (var entry : inner.entrySet()) {
                yetToFixGotos.peek().computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
            }
        }
        dangerZone.pop();

        // fixup all inner breaks
        if (rVal.isLoop) {
            var patch = breaksToPatch.get(rVal);
            if (patch != null) {
                int curClosables = getCurFuncClosableCnt();
                for (var b : patch) {
                    // the closable count of this break is initialized with the total amount of closables at that time,
                    // but we only want to close values that are not live anymore after the loop scope
                    b.closeCnt -= curClosables;
                }
                breaksToPatch.remove(rVal);
            }
        }
        return rVal;
    }

    public boolean paramsDefined() {
        var cur = curScope;
        while (!cur.isFunctionBorder) {
            cur = cur.parent;
        }
        return cur.hasParamsArg;
    }

    public int getCurFuncClosableCnt() {
        VarScope c = curScope;
        int cnt = 0;
        while (!c.isFunctionBorder) {
            cnt += c.getClosableCount();
            c = c.parent;
        }
        cnt += c.getClosableCount();
        return cnt;
    }

    private int[] getCurFuncClsCntPerScope() {
        var closeDefList = new ArrayList<Integer>();
        var cs = curScope;
        while (!cs.isFunctionBorder) {
            closeDefList.add(cs.getClosableCount());
            cs = cs.parent;
        }
        closeDefList.add(cs.getClosableCount());
        Collections.reverse(closeDefList);
        return closeDefList.stream().mapToInt(i -> i).toArray();
    }

    private int[] getCurFuncLocalsCntPerScope() {
        var localDefList = new ArrayList<Integer>();
        var cs = curScope;
        while (!cs.isFunctionBorder) {
            localDefList.add(cs.getLocalsCount());
            cs = cs.parent;
        }
        localDefList.add(cs.getLocalsCount());
        Collections.reverse(localDefList);
        return localDefList.stream().mapToInt(i -> i).toArray();
    }

    private Triple<Integer, Integer, Boolean>[] getScopeStats() {
        var stats = new ArrayList<Triple<Integer, Integer, Boolean>>();
        var cs = curScope;
        while (!cs.isFunctionBorder) {
            stats.add(new Triple<>(cs.getLocalsCount(), cs.getClosableCount(), cs.isInlined));
            cs = cs.parent;
        }
        stats.add(new Triple<>(cs.getLocalsCount(), cs.getClosableCount(), cs.isInlined));
        Collections.reverse(stats);
        return stats.toArray(Triple[]::new);
    }

    public int getMaxFuncLocals() {
        return getMaxFuncLocalsRecursive(curScope);
    }

    private static int getMaxFuncLocalsRecursive(VarScope scope) {
        int innerMax = 0;
        for (var s : scope.children) {
            if (!s.isFunctionBorder) {
                innerMax = Math.max(innerMax, getMaxFuncLocalsRecursive(s));
            }
        }
        return innerMax + scope.getLocalsCount();
    }

    public SpecificVarInfo add(Token ident, boolean isConst, boolean isClosable) {
        return curScope.add(ident, isConst, isClosable);
    }

    public SpecificVarInfo get(String ident) {
        return curScope.get(ident, false);
    }

    private LabelInfo addLabel(Token label) {
        return curScope.addLabel(label, getCurFuncLocalsCntPerScope(), getCurFuncClsCntPerScope());
    }

    private LabelInfo getLabel(String ident) {
        return curScope.getLabel(ident);
    }

    public BreakNode generateBreakNode(Position pos) {
        var cs = curScope;
        int escapeCnt = 0;
        while (!cs.isFunctionBorder && !cs.isLoop) {
            if (!cs.isInlined) {
                escapeCnt++;
            }
            cs = cs.parent;
        }
        if (cs.isFunctionBorder) {
            return null;
        }
        // function- and loop borders are never inlined
        escapeCnt++;
        var b = new BreakNode(pos, escapeCnt, getCurFuncClosableCnt());
        breaksToPatch.computeIfAbsent(cs, s -> new Stack<>()).push(b);
        return b;
    }

    public GotoNode generateGoto(Token gttk) {
        var lbl = getLabel(gttk.stVal());
        if (lbl != null) {
            // back-jump
            var stats = getScopeStats();
            int toClose = 0;
            int localDiff = 0;
            int exits = 0;
            int lcpsl = lbl.closablesPerScope.length;
            for (int i = stats.length - 1; i >= lcpsl; i--) {
                localDiff += stats[i].x();
                toClose += stats[i].y();
                if (!stats[i].z()) {
                    exits++;
                }
            }
            localDiff += stats[lcpsl - 1].x() - lbl.localsPerScope[lcpsl - 1];
            toClose += stats[lcpsl - 1].y() - lbl.closablesPerScope[lcpsl - 1];
            int localsForLabel = 0;
            for (int lc : lbl.localsPerScope) {
                localsForLabel += lc;
            }
            return new GotoNode(gttk.pos(), lbl, exits, toClose, 0, localsForLabel, localDiff);
        } else {
            // forward jump
            /*-
             * How to patch this goto node:
             *  - count all non-inlined loops from goto depth to label depth -> scope exits
             *  - sum all cDefs the goto has seen in scopes lower than the label -> closableCnt
             *  - subtract cloables defined in label scope at goto from defined at label -> closePatchCnt
             */
            var stats = getScopeStats();
            var g = new GotoNode(gttk.pos());
            yetToFixGotos.peek().computeIfAbsent(gttk.stVal(), k -> new ArrayList<>()).add(new Triple<>(g, stats, gttk.pos()));
            return g;
        }
    }

    public LabelNode generateLabel(Token t) {
        var info = addLabel(t);
        var node = new LabelNode(t.pos(), info);

        // check for fixup
        var gotos = yetToFixGotos.peek().get(t.stVal());
        if (gotos == null) {
            // no gotos to fix, we are done here
            return node;
        }

        // fix gotos
        int[] clps = getCurFuncLocalsCntPerScope();
        int[] ccps = getCurFuncClsCntPerScope();

        for (Triple<GotoNode, Triple<Integer, Integer, Boolean>[], Position> gtt : gotos) {
            int scopeExits = 0;
            int toClose = 0;
            for (int i = gtt.y().length - 1; i >= ccps.length; i--) {
                toClose += gtt.y()[i].y();
                if (!gtt.y()[i].z()) {
                    // this scope is not inlined, therefore we need to exit this scope in the vm
                    scopeExits++;
                }
            }
            toClose += gtt.y()[ccps.length - 1].y();
            int toPatch = ccps[ccps.length - 1] - gtt.y()[ccps.length - 1].y();
            if (gtt.y()[clps.length - 1].x() < clps[clps.length - 1]) {
                // we jump into scopes of local variables which is only allowed if the label is the last statement in a block
                dangerZone.peek().add(new Tuple<>(gtt.x(), gtt.z()));
            }
            gtt.x().label = info;
            gtt.x().scopeExits = scopeExits;
            gtt.x().closableCnt = toClose;
            gtt.x().closePatchCnt = toPatch;
        }
        yetToFixGotos.peek().remove(t.stVal());
        return node;
    }

    public void labelNotLast() {
        if (!dangerZone.peek().isEmpty()) {
            // jump into scope of local variable
            var elem = dangerZone.peek().get(0);
            throw new LuaSemanticException(elem.y(), "jump into scope of local variable");
        }
    }
}
