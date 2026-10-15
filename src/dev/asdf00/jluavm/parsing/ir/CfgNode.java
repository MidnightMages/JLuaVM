package dev.asdf00.jluavm.parsing.ir;

import dev.asdf00.jluavm.parsing.ir.ast.AST;

import java.util.ArrayList;

public class CfgNode {
    public ArrayList<AST> statements;

    private CfgNode defaultSuccessor; // dont write from outside directly as this requires updating the predecessor node collection in the listed CfgNode node

    public AST ifCondition;
    private CfgNode branchingSuccessor; // dont write from outside directly as this requires updating the predecessor node collection in the listed CfgNode node

    public ArrayList<CfgNode> predecessors;

    public CfgNode(ArrayList<AST> statements, CfgNode defaultSuccessor, AST ifCondition, CfgNode branchingSuccessor) {
        this.statements = statements;
        if (defaultSuccessor != null)
            setDefaultSuccessor(defaultSuccessor);
        this.ifCondition = ifCondition;
        if (branchingSuccessor != null)
            setBranchingSuccessor(branchingSuccessor);
        this.predecessors = new ArrayList<>();
    }

    public CfgNode(ArrayList<AST> statements) {
        this.statements = statements;
        this.predecessors = new ArrayList<>();
    }

    public CfgNode() {
        this(new ArrayList<>());
    }

    public void setDefaultSuccessor(CfgNode successor) {
        assert defaultSuccessor == null;
        defaultSuccessor = successor;
        successor.predecessors.add(this);
    }

    public void setBranchingSuccessor(CfgNode successor) {
        assert branchingSuccessor == null;
        branchingSuccessor = successor;
        successor.predecessors.add(this);
    }

    public CfgNode getBranchingSuccessor() {
        return branchingSuccessor;
    }

    public CfgNode getDefaultSuccessor() {
        return defaultSuccessor;
    }

    public CfgNode getTrueIfSuccessor() {
        assert ifCondition != null;
        return branchingSuccessor;
    }

    public CfgNode getFalseIfSuccessor() {
        assert ifCondition != null;
        return defaultSuccessor;
    }
}
