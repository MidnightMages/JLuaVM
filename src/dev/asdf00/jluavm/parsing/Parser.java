package dev.asdf00.jluavm.parsing;

import dev.asdf00.jluavm.parsing.container.*;
import dev.asdf00.jluavm.exceptions.loading.LuaParserException;
import dev.asdf00.jluavm.exceptions.LuaLoadingException;
import dev.asdf00.jluavm.exceptions.loading.LuaSemanticException;
import dev.asdf00.jluavm.parsing.ir.IRBlock;
import dev.asdf00.jluavm.parsing.ir.IRFunction;
import dev.asdf00.jluavm.parsing.ir.Node;
import dev.asdf00.jluavm.parsing.ir.controlflow.BreakNode;
import dev.asdf00.jluavm.parsing.ir.controlflow.FunctionCallNode;
import dev.asdf00.jluavm.parsing.ir.controlflow.GotoNode;
import dev.asdf00.jluavm.parsing.ir.operations.AssignmentNode;
import dev.asdf00.jluavm.parsing.ir.operations.BinaryOpNode;
import dev.asdf00.jluavm.parsing.ir.values.ConstantNode;
import dev.asdf00.jluavm.parsing.ir.operations.UnaryOpNode;
import dev.asdf00.jluavm.parsing.ir.values.ConstructedTableNode;
import dev.asdf00.jluavm.parsing.ir.values.DeRefNode;
import dev.asdf00.jluavm.parsing.ir.values.LocalAccessNode;
import dev.asdf00.jluavm.utils.Triple;
import dev.asdf00.jluavm.utils.Tuple;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

import static dev.asdf00.jluavm.parsing.container.TokenType.*;

public class Parser {
    private final Supplier<String> fClassNameGenerator;
    private final Supplier<String> tempVarNameGen;
    private final SymTable symTab;
    private final Lexer lexer;
    private TokenType ltok = EOF;  // TokenType of la
    private Token cur;  // current token
    private Token la;  // lookahead token
    private Token lla;  // lookahead token 2

    public Parser(Supplier<String> fClassNameGenerator, String input) {
        this.fClassNameGenerator = fClassNameGenerator;
        symTab = new SymTable();
        lexer = new Lexer(input);

        tempVarNameGen = new Supplier<>() {
            long id = 0;

            @Override
            public String get() {
                return "_tempVar$" + (id++);
            }
        };
    }

    private void check(TokenType type) {
        if (ltok != type) {
            throw new LuaParserException(la.pos(), "(At %s) Expected <%s>, got <%s>".formatted(la.pos(), type.rep, ltok.rep));
        }
        scan();
    }

    private void scan() {
        cur = la;
        la = lla;
        lla = lexer.next();
        ltok = la.type();
    }

    private boolean isAssignable() {
        return cur.type() == RBRAK || cur.type() == IDENT;
    }

    private void define(Token ident, int attributes) {
        if (!symTab.add(ident, (attributes & 1) == 1, (attributes & 2) == 2)) {
            throw new LuaSemanticException(ident.pos(), "'%s' is defined twice!".formatted(cur.stVal()));
        }
    }

    private void enterScope(boolean isFunctionBorder, boolean isLoop) {
        symTab.enterScope(isFunctionBorder, isLoop);
        if (isFunctionBorder) {
            var f = new IRFunction(fClassNameGenerator.get());
            funcStack.push(f);
            funcCur = f;
            blockStack.push(f);
            blockCur = f;
        } else {
            var b = new IRBlock();
            blockStack.push(b);
            blockCur = b;
        }
    }

    private void exitScope() {
        var exited = symTab.exitScope();
        if (exited.isFunctionBorder) {
            if (funcCur.needFixup.size() > 0) {
                GotoNode errGoto = funcCur.needFixup.entrySet().stream().findFirst().get().getValue().get(0);
                throw new LuaSemanticException(errGoto.pos, "No jump target found for 'goto %s'".formatted(errGoto.stLabel));
            }
            if (funcStack.size() > 0) {
                funcCur = funcStack.pop();
            }
        }
        if (blockStack.size() > 0) {
            blockCur = blockStack.pop();
        }
    }

    private GotoNode generateGoto() {
        LabelInfo target = symTab.getLabel(cur.stVal());
        if (target != null) {
            // this is a back jump
            target.isUsed = true;
            var closableList = new ArrayList<VarInfo>();
            // close all defined from inner scopes
            int lblDepth = target.definedVars.length - 1;
            var gotoDefined = symTab.getDefinedVars();
            for (int i = gotoDefined.length - 1; i > lblDepth; i--) {
                for (int j = gotoDefined[i].length - 1; i > 0; i--) {
                    closableList.add(gotoDefined[i][j]);
                }
            }
            // close surplus of variables in current scope
            for (int i = gotoDefined[lblDepth].length; i >= target.definedVars[lblDepth].length; i--) {
                closableList.add(gotoDefined[lblDepth][i]);
            }
            return new GotoNode(cur.pos(), cur.stVal(), target, closableList.toArray(VarInfo[]::new));
        } else {
            // this is a forward jump, and we do not know where this will lead us
            var gt = new GotoNode(cur.pos(), cur.stVal(), symTab.getDefinedVars());
            funcCur.needFixup.computeIfAbsent(cur.stVal(), ignore -> new ArrayList<>()).add(gt);
            return gt;
        }
    }

    // =================================================================================================================
    //    PARSE STATE     PARSE STATE     PARSE STATE     PARSE STATE     PARSE STATE     PARSE STATE     PARSE STATE
    // =================================================================================================================

    private final Stack<IRFunction> funcStack = new Stack<>();
    private IRFunction funcCur;

    private final Stack<IRBlock> blockStack = new Stack<>();
    private IRBlock blockCur;

    // =================================================================================================================
    //    PARSING   PARSING   PARSING   PARSING   PARSING   PARSING   PARSING   PARSING   PARSING   PARSING   PARSING
    // =================================================================================================================

    public IRFunction parse() throws LuaLoadingException {
        cur = null;
        la = lexer.next();
        lla = lexer.next();
        ltok = la.type();
        return Chunk();
    }

    private IRFunction Chunk() {
        enterScope(true, false);
        Block();
        var topLevelFunction = funcCur;
        exitScope();
        check(EOF);
        return topLevelFunction;
    }

    private void Block() {
        while (STAT_START.contains(ltok)) {
            Stat();
        }
        if (ltok == RETURN) {
            scan();
            if (EXP_START.contains(ltok)) {
                ExpList();
            }
            if (ltok == SEMICOLON) {
                scan();
            }
        }
    }

    private static final EnumSet<TokenType> STAT_START = EnumSet.of(SEMICOLON, IDENT, LPAR, DCOLON, BREAK, GOTO, DO, WHILE, REPEAT, IF, FOR, FUNCTION, LOCAL);

    private Node Stat() {
        Node statement = null;
        switch (ltok) {
            case SEMICOLON -> {
                // nop
                scan();
            }
            case BREAK -> {
                scan();
                var ls = symTab.getNextLoop();
                if (ls == null) {
                    throw new LuaSemanticException(cur.pos(), "'break' is not inside a loop");
                }
                VarInfo[][] loopScopeVars = ls.getDefinedVars();
                VarInfo[][] breakDefinedVars = symTab.getDefinedVars();
                int loopScopeDepth = loopScopeVars.length - 1;
                var closableList = new ArrayList<VarInfo>();
                for (int i = breakDefinedVars.length - 1; i >= loopScopeDepth; i--) {
                    for (int j = breakDefinedVars[i].length - 1; i > 0; i--) {
                        closableList.add(breakDefinedVars[i][j]);
                    }
                }
                statement = new BreakNode(ls, closableList.toArray(VarInfo[]::new));
            }
            case GOTO -> {
                scan();
                check(IDENT);
                statement = generateGoto();
            }
            case DCOLON -> {
                // label
                scan();
                check(IDENT);
                statement = symTab.addLabel(cur, funcCur.needFixup);
                check(DCOLON);
            }
            case DO -> {
                scan();
                enterScope(false, false);
                Block();
                exitScope();
                check(END);
            }
            case WHILE -> {
                scan();
                Exp();
                check(DO);
                Block();
                check(END);
            }
            case REPEAT -> {
                scan();
                enterScope(false, true);
                Block();
                check(UNTIL);
                Exp();
                exitScope();
            }
            case IF -> {
                scan();
                Exp();
                check(THEN);
                enterScope(false, false);
                Block();
                exitScope();
                while (ltok == ELSEIF) {
                    scan();
                    Exp();
                    check(THEN);
                    enterScope(false, false);
                    Block();
                    exitScope();
                }
                if (ltok == ELSE) {
                    scan();
                    enterScope(false, false);
                    Block();
                    exitScope();
                }
                check(END);
            }
            case FOR -> {
                scan();
                enterScope(false, true);
                check(IDENT);
                if (ltok == ASSIGN) {
                    scan();
                    // normal FOR
                    // start
                    Exp();
                    check(COMMA);
                    // end
                    Exp();
                    if (ltok == COMMA) {
                        scan();
                        // step
                        Exp();
                    }
                } else {
                    // foreach
                    while (ltok == COMMA) {
                        scan();
                        check(IDENT);
                    }
                    check(IN);
                    ExpList();
                }
                check(DO);
                Block();
                exitScope();
                check(END);
            }
            case FUNCTION -> {
                scan();
                check(IDENT);
                define(cur, 0);
                while (ltok == DOT) {
                    scan();
                    check(IDENT);
                }
                boolean hasSelf = false;
                if (ltok == COLON) {
                    scan();
                    hasSelf = true;
                    check(IDENT);
                }
                FuncBody(hasSelf);
            }
            case LOCAL -> {
                scan();
                if (ltok == FUNCTION) {
                    scan();
                    check(IDENT);
                    define(cur, 0);
                    FuncBody(false);
                } else {
                    check(IDENT);
                    Token locVar = cur;
                    int attributes = Attrib();
                    define(locVar, attributes);
                    while (ltok == COMMA) {
                        scan();
                        check(IDENT);
                        locVar = cur;
                        attributes = Attrib();
                        define(locVar, attributes);
                    }
                    if (ltok == ASSIGN) {
                        scan();
                        ExpList();
                    }
                }
            }
            case IDENT, LPAR -> {
                StatExp();
            }
        }
        return statement;
    }

    private int Attrib() {
        int rVal = 0;
        if (ltok == LT) {
            scan();
            check(IDENT);
            if ("const".equals(cur.stVal())) {
                rVal = 1;
            } else if ("close".equals(cur.stVal())) {
                rVal = 3;
            } else {
                throw new LuaSemanticException(cur.pos(), "Only 'const' and 'close' are allowed as attributes, not '%s'!".formatted(cur.stVal()));
            }
            check(GT);
        }
        return rVal;
    }

    private static final EnumSet<TokenType> DEREF_OR_FUNCCALL_START = EnumSet.of(LBRAK, DOT, COLON, LPAR, LITERAL_STRING, LBRAC);

    private Node StatExp() {
        SpecificVarInfo info;
        boolean onlyIdent;
        Node result;
        if (ltok == LPAR) {
            scan();
            result = Exp();
            check(RPAR);
            info = null;
            onlyIdent = false;
            if (ltok == LBRAK || ltok == DOT) {
                result = DeRef(result);
            } else {
                result = FuncCall(result);
            }
        } else {
            check(IDENT);
            info = symTab.get(cur.stVal());
            result = info == null ? new DeRefNode(new ConstantNode("_ENV"), ConstantNode.ofString(cur.stVal())) : new LocalAccessNode(info);
            onlyIdent = true;
        }
        loop:
        for (; ; ) {
            switch (ltok) {
                case LBRAK, DOT -> {
                    result = DeRef(result);
                }
                case COLON, LPAR, LITERAL_STRING, LBRAC -> {
                    result = FuncCall(result);
                }
                default -> {
                    break loop;
                }
            }
            onlyIdent = false;
        }
        if (isAssignable()) {
            var assignTargets = new ArrayList<Node>();
            assignTargets.add(result);
            var qLocals = new ArrayList<Tuple<SpecificVarInfo, Boolean>>();
            qLocals.add(new Tuple<>(info, onlyIdent));
            while (ltok == COMMA) {
                scan();
                var packedInfo = ValExp();
                assignTargets.add(packedInfo.x());
                qLocals.add(new Tuple<>(packedInfo.y(), packedInfo.z()));
                if (!isAssignable()) {
                    throw new LuaParserException(la.pos(), "Expected <%s>, got <%s>".formatted(DOT.rep, ltok.rep));
                }
            }
            check(ASSIGN);
            qLocals.forEach(p -> {
                if (p.x() != null && p.y()) {
                    if (p.x().baseInfo().isConstant()) {
                        throw new LuaSemanticException(cur.pos(), "Constant variable must not be written");
                    }
                    p.x().baseInfo().setWritten();
                }
            });
            Node[] expressions = ExpList();
            result = new AssignmentNode(assignTargets.toArray(Node[]::new), expressions);
        }
        return result;
    }

    private Triple<Node, SpecificVarInfo, Boolean> ValExp() {
        SpecificVarInfo info;
        boolean onlyIdent;
        Node result;
        if (ltok == LPAR) {
            scan();
            result = Exp();
            check(RPAR);
            info = null;
            onlyIdent = false;
        } else {
            check(IDENT);
            info = symTab.get(cur.stVal());
            result = info == null ? new DeRefNode(new ConstantNode("_ENV"), ConstantNode.ofString(cur.stVal())) : new LocalAccessNode(info);
            onlyIdent = true;
        }
        loop:
        for (; ; ) {
            switch (ltok) {
                case LBRAK, DOT -> {
                    result = DeRef(result);
                }
                case COLON, LPAR, LITERAL_STRING, LBRAC -> {
                    result = FuncCall(result);
                }
                default -> {
                    break loop;
                }
            }
            onlyIdent = false;
        }
        return new Triple<>(result, info, onlyIdent);
    }

    private Node DeRef(Node target) {
        Node index;
        if (ltok == LBRAK) {
            scan();
            index = Exp();
            check(RBRAK);
        } else {
            check(DOT);
            check(IDENT);
            index = ConstantNode.ofString(cur.stVal());
        }
        return new DeRefNode(target, index);
    }

    private Node FuncCall(Node callable) {
        Node object = null;
        Node func = callable;
        if (ltok == COLON) {
            scan();
            object = callable;
            check(IDENT);
            func = new ConstantNode("LuaObject.ofB64(\"%s\")".formatted(Base64.getEncoder().encode(cur.stVal().getBytes(StandardCharsets.UTF_8))));
        }
        Node[] args = Args();
        return new FunctionCallNode(object, func, args);
    }

    private Node[] ExpList() {
        var es = new ArrayList<Node>();
        es.add(Exp());
        while (ltok == COMMA) {
            scan();
            es.add(Exp());
        }
        return es.toArray(Node[]::new);
    }

    private static final EnumSet<TokenType> EXP_START = EnumSet.of(NIL, FALSE, TRUE, NUMERAL, LITERAL_STRING, TDOT,
            FUNCTION, LPAR, IDENT, NOT, HASH, SUB, BXOR, LBRAC);

    private Node Exp() {
        // or
        var result = BinOp1();
        while (ltok == OR) {
            var op = ltok;
            scan();
            result = new BinaryOpNode(result, BinOp1(), op);
        }
        return result;
    }

    private Node BinOp1() {
        // and
        Node result = BinOp2();
        while (ltok == AND) {
            var op = ltok;
            scan();
            result = new BinaryOpNode(result, BinOp2(), op);
        }
        return result;
    }

    private Node BinOp2() {
        // < > <= >= ~= ==
        Node result = BinOp3();
        loop:
        for (; ; ) {
            switch (ltok) {
                case LT, GT -> {
                    var op = ltok;
                    scan();
                    var bo3 = BinOp3();
                    result = new BinaryOpNode(op == LT ? result : bo3, op == LT ? bo3 : result, op);
                }
                case LE, GE -> {
                    var op = ltok;
                    scan();
                    var bo3 = BinOp3();
                    result = new BinaryOpNode(op == LE ? result : bo3, op == LE ? bo3 : result, op);
                }
                case EQ, NE -> {
                    var op = ltok;
                    scan();
                    result = new BinaryOpNode(result, BinOp3(), EQ);
                    if (op == NE)
                        result = new UnaryOpNode(result, NOT);
                }
                default -> {
                    break loop;
                }
            }
        }
        return result;
    }

    private Node BinOp3() {
        // |
        var result = BinOp4();
        while (ltok == BOR) {
            var op = ltok;
            scan();
            Node y = BinOp4();
            result = BinaryOpNode.bitwise(op.metatableFuncNameBinary, result, y);
        }
        return result;
    }

    private Node BinOp4() {
        // ~
        var result = BinOp5();
        while (ltok == BXOR) {
            var op = ltok;
            scan();
            Node y = BinOp5();
            result = BinaryOpNode.bitwise(op.metatableFuncNameBinary, result, y);
        }
        return result;
    }

    private Node BinOp5() {
        // &
        var result = BinOp6();
        while (ltok == BAND) {
            var op = ltok;
            scan();
            Node y = BinOp6();
            result = BinaryOpNode.bitwise(op.metatableFuncNameBinary, result, y);
        }
        return result;
    }

    private Node BinOp6() {
        // << >>
        var result = BinOp7();
        while (ltok == SHL || ltok == SHR) {
            var op = ltok;
            scan();
            Node y = BinOp7();
            result = BinaryOpNode.bitwise(op.metatableFuncNameBinary, result, y);
        }
        return result;
    }

    private Node BinOp7() {
        // ..
        var result = BinOp8();
        while (ltok == DDOT) {
            var op = ltok;
            scan();
            Node y = BinOp8();
            result = BinaryOpNode.arithmetic(op.metatableFuncNameBinary, result, y);
        }
        return result;
    }

    private Node BinOp8() {
        // + -
        var result = BinOp9();
        for (; ; ) {
            if (ltok == ADD || ltok == SUB) {
                var op = ltok;
                scan();
                Node y = BinOp9();
                result = BinaryOpNode.arithmetic(op.metatableFuncNameBinary, result, y);
            } else {
                break;
            }
        }
        return result;
    }

    private Node BinOp9() {
        // * / // %
        Node result = UnOp();
        loop:
        for (; ; ) {
            switch (ltok) {
                case MULT, DIV, FDIV, MOD -> {
                    var op = ltok;
                    scan();
                    Node y = UnOp();
                    result = BinaryOpNode.arithmetic(op.metatableFuncNameBinary, result, y);
                }
                default -> {
                    break loop;
                }
            }
        }
        return result;
    }

    private Node UnOp() {
        // not # - ~
        if (ltok == NOT || ltok == HASH || ltok == SUB || ltok == BXOR) {
            var op = ltok;
            scan();
            return new UnaryOpNode(UnOp(), op);
        }
        return BinOp10();
    }

    private Node BinOp10() {
        // ^
        Node result = TermExp();
        while (ltok == EXPONENT) {
            var op = ltok;
            scan();
            Node y = TermExp();
            result = BinaryOpNode.arithmetic(op.metatableFuncNameBinary, result, y);
        }
        return result;
    }

    private Node TermExp() {
        // constants, funcdef or ValExp
        switch (ltok) {
            case NIL -> {
                scan();
                return ConstantNode.nil();
            }
            case TRUE, FALSE -> {
                scan();
                return ConstantNode.ofBool(cur.type() == TRUE);
            }
            case NUMERAL -> {
                scan();
                return cur.nVal() < 0 ? ConstantNode.ofLong(cur.lVal()) : ConstantNode.ofDouble(cur.nVal());
            }
            case LITERAL_STRING -> {
                scan();
                return ConstantNode.ofB64(cur.stVal());
            }
            case TDOT -> {
                scan();
                if (!funcCur.hasParams) {
                    throw new LuaSemanticException(cur.pos(), "cannot use '...' outside a vararg function");
                }
                return new LocalAccessNode(symTab.get("..."));
            }
            case FUNCTION -> {
                scan();
                // TODO definition
                FuncBody(false);
            }
            case LBRAC -> {
                return TableConstructor();
            }
            default -> {
                return ValExp().x();
            }
        }
        throw new UnsupportedOperationException("not implemented");
    }

    private void FuncBody(boolean hasSelf) {
        // TODO definition
        enterScope(true, false);
        check(LPAR);
        if (hasSelf) {
            define(new Token(IDENT, cur.pos(), "self"), 0);
        }
        if (ltok == TDOT || ltok == IDENT) {
            ParList();
        }
        check(RPAR);
        Block();
        exitScope();
        check(END);
    }

    private void ParList() {
        // TODO definition
        if (ltok == TDOT) {
            scan();
            funcCur.hasParams = true;
        } else {
            check(IDENT);
            define(cur, 0);
            while (ltok == COMMA && lla.type() != TDOT) {
                scan();
                check(IDENT);
                define(cur, 0);
            }
            if (ltok == COMMA) {
                scan();
                check(TDOT);
                funcCur.hasParams = true;
            }
        }
    }

    private static final EnumSet<TokenType> ARGS_START = EnumSet.of(LPAR, LITERAL_STRING, LBRAC);

    private Node[] Args() {
        Node[] result;
        if (ltok == LPAR) {
            scan();
            if (EXP_START.contains(ltok)) {
                result = ExpList();
            } else {
                result = new Node[0];
            }
            check(RPAR);
        } else if (ltok == LITERAL_STRING) {
            scan();
            result = new Node[]{ConstantNode.ofB64(cur.stVal())};
        } else {
            result = new Node[]{TableConstructor()};
        }
        return result;
    }

    private Node TableConstructor() {
        check(LBRAC);
        Node[] keyVals;
        if (ltok == RBRAC) {
            scan();
            keyVals = new Node[0];
        } else {
            keyVals = FieldList();
        }
        return new ConstructedTableNode(keyVals);
    }

    private Node[] FieldList() {
        var fieldList = new ArrayList<Node>();
        Field(fieldList);
        while ((ltok == COMMA || ltok == SEMICOLON) && lla.type() != RBRAC) {
            FieldSep();
            Field(fieldList);
        }
        if (ltok == COMMA || ltok == SEMICOLON) {
            FieldSep();
        }
        check(RBRAC);
        return fieldList.toArray(Node[]::new);
    }

    private void Field(ArrayList<Node> fieldList) {
        if (ltok == LBRAK) {
            scan();
            fieldList.add(Exp());
            check(RBRAK);
            check(ASSIGN);
            fieldList.add(Exp());
        } else if (ltok == IDENT && lla.type() == ASSIGN) {
            scan();
            fieldList.add(ConstantNode.ofString(cur.stVal()));
            scan();  // ASSIGN
            fieldList.add(Exp());
        } else {
            fieldList.add(new ConstantNode("null")); // placeholder value for replacement in LuaTable$.of
            fieldList.add(Exp());
        }
    }

    private void FieldSep() {
        if (ltok == COMMA || ltok == SEMICOLON) {
            scan();
        }
    }
}
