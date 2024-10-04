package dev.asdf00.jluavm.parsing.ir;

import java.util.ArrayList;

public class IRBlock extends Node {
    public ArrayList<GotoNode> needFixup = new ArrayList<>();
}
