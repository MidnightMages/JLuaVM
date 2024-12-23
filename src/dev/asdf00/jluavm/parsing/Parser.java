package dev.asdf00.jluavm.parsing;

import dev.asdf00.jluavm.exceptions.LuaLoadingException;
import dev.asdf00.jluavm.exceptions.loading.InternalLuaLoadingError;
import dev.asdf00.jluavm.exceptions.loading.LuaParserException;
import dev.asdf00.jluavm.exceptions.loading.LuaSemanticException;
import dev.asdf00.jluavm.parsing.container.*;
import dev.asdf00.jluavm.parsing.ir.*;
import dev.asdf00.jluavm.parsing.ir.controlflow.*;
import dev.asdf00.jluavm.parsing.ir.operations.*;
import dev.asdf00.jluavm.parsing.ir.values.*;
import dev.asdf00.jluavm.utils.Triple;
import dev.asdf00.jluavm.utils.Tuple;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Stack;

import static dev.asdf00.jluavm.parsing.container.TokenType.*;

public final class Parser {
    private final SymTable symTab;
    private final Lexer lexer;
    private TokenType ltok = EOF;  // TokenType of la
    private Token cur;  // current token
    private Token la;  // lookahead token
    private Token lla;  // lookahead token 2

    public Parser(String input) {
        symTab = new SymTable();
        lexer = new Lexer(input);
    }

    // =================================================================================================================
    //           UTIL METHODS        UTIL METHODS        UTIL METHODS        UTIL METHODS        UTIL METHODS
    // =================================================================================================================

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

    private SpecificVarInfo define(Token ident, int attributes) {
        SpecificVarInfo info = symTab.add(ident, (attributes & 1) == 1, (attributes & 2) == 2);
        if (info == null) {
            throw new LuaSemanticException(ident.pos(), "'%s' is defined twice!".formatted(cur.stVal()));
        }
        return info;
    }

    private SpecificVarInfo defineInternal(String name) {
        return defineInternal(name, 0);
    }

    private SpecificVarInfo defineInternal(String name, int attributes) {
        return define(new Token(IDENT, cur.pos(), name), attributes);
    }

    private Node genAccess(Position pos, SpecificVarInfo info, String ident) {
        if (info != null) {
            return new LocalAccessNode(pos, info);
        }
        // lookup local definition for _ENV
        var env = symTab.get("_ENV");
        if (env != null) {
            // we index the local _ENV value
            return new DeRefNode(pos, new LocalAccessNode(pos, env), ConstantNode.ofIdent(pos, ident));
        } else {
            if ("_ENV".equals(ident)) {
                // here we access _ENV itself
                return new EnvAccessNode(pos);
            } else {
                // this seems to be a global value, therefore we index the environment
                return new DeRefNode(pos, new EnvAccessNode(pos), ConstantNode.ofIdent(pos, ident));
            }
        }
    }

    private Node getEnv(Position pos) {
        // lookup local definition for _ENV
        var env = symTab.get("_ENV");
        return env == null ? new EnvAccessNode(pos) : new LocalAccessNode(pos, env);
    }

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
        Position _pos = la.pos();
        symTab.enterFunctionScope(true);
        var tdot = new Token(TDOT, _pos, "...");
        symTab.add(tdot, false, false);
        var innerStats = Block();
        int maxLocalCnt = symTab.getMaxFuncLocals();
        VarScope scp = symTab.exitScope();
        check(EOF);
        return new IRFunction(_pos, innerStats.toArray(Node[]::new), scp.getLocalsCount(), scp.getClosableCount(), maxLocalCnt, 1, true);
    }

    private ArrayList<Node> Block() {
        var statements = new ArrayList<Node>();
        while (STAT_START.contains(ltok)) {
            Node s = Stat();
            if (s != null) {
                statements.add(s);
            }
        }
        if (ltok == RETURN) {
            scan();
            Position pos = cur.pos();
            Node[] returnValues;
            if (EXP_START.contains(ltok)) {
                returnValues = ExpList();
            } else {
                returnValues = new Node[0];
            }
            if (ltok == SEMICOLON) {
                scan();
            }
            if (symTab.getCurFuncClosableCnt() == 0 && returnValues.length == 1 && returnValues[0] instanceof FunctionCallNode tailCall) {
                statements.add(new TailCallNode(pos, tailCall));
            } else {
                statements.add(new ReturnNode(pos, returnValues, symTab.getCurFuncClosableCnt()));
            }
        }
        return statements;
    }

    private static final EnumSet<TokenType> STAT_START = EnumSet.of(SEMICOLON, IDENT, LPAR, DCOLON, BREAK, GOTO, DO, WHILE, REPEAT, IF, FOR, FUNCTION, LOCAL);

    private Node Stat() {
        Node statement;
        Position _pos = la.pos();
        switch (ltok) {
            case SEMICOLON -> {
                // nop
                scan();
                statement = null;
            }
            case BREAK -> {
                scan();
                symTab.labelNotLast();
                var bNode = symTab.generateBreakNode(_pos);
                if (bNode == null) {
                    throw new LuaSemanticException(cur.pos(), "'break' is not inside a loop");
                }
                statement = bNode;
            }
            case GOTO -> {
                scan();
                symTab.labelNotLast();
                check(IDENT);
                statement = symTab.generateGoto(cur);
            }
            case DCOLON -> {
                // label
                scan();
                check(IDENT);
                statement = symTab.generateLabel(cur);
                check(DCOLON);
            }
            case DO -> {
                scan();
                symTab.labelNotLast();
                symTab.enterPlainScope(true);
                var innerStats = Block();
                check(END);
                VarScope scp = symTab.exitScope();
                statement = new DoEndNode(_pos, innerStats.toArray(Node[]::new), scp.getPrevFunctionLocalsCount(), scp.getLocalsCount(), scp.getClosableCount());
            }
            case WHILE -> {
                scan();
                symTab.labelNotLast();
                Node entryCond = Exp();
                check(DO);
                symTab.enterLoopScope();
                var innerStats = Block();
                check(END);
                VarScope scp = symTab.exitScope();
                statement = new IfNode(_pos, entryCond, new IRBlock(_pos, innerStats.toArray(Node[]::new), entryCond, true,
                        scp.getLocalsCount(), scp.getClosableCount()));
            }
            case REPEAT -> {
                scan();
                symTab.labelNotLast();
                symTab.enterLoopScope();
                var innerStats = Block();
                check(UNTIL);
                Node exitCond = Exp();
                VarScope scp = symTab.exitScope();
                statement = new PlainInnerBlockNode(_pos, new IRBlock(_pos, innerStats.toArray(Node[]::new), exitCond, false,
                        scp.getLocalsCount(), scp.getClosableCount()));
            }
            case IF -> {
                scan();
                symTab.labelNotLast();
                Node condition = Exp();
                check(THEN);
                symTab.enterPlainScope(false);
                Position thenBPos = cur.pos();
                var thenBlock = Block();
                VarScope thenScp = symTab.exitScope();
                var elifConds = new ArrayList<Node>();
                var elifBlocks = new ArrayList<ArrayList<Node>>();
                var elifPoses = new ArrayList<Position>();
                var elifScps = new ArrayList<VarScope>();
                while (ltok == ELSEIF) {
                    scan();
                    var elifCond = Exp();
                    elifConds.add(elifCond);
                    check(THEN);
                    symTab.enterPlainScope(false);
                    elifPoses.add(cur.pos());
                    var elifBlock = Block();
                    elifBlocks.add(elifBlock);
                    elifScps.add(symTab.exitScope());
                }
                Position elsePos = null;
                ArrayList<Node> elseBlock = null;
                VarScope elseScp = VarScope.EMPTY_DUMMY;
                if (ltok == ELSE) {
                    scan();
                    symTab.enterLoopScope();
                    elsePos = cur.pos();
                    elseBlock = Block();
                    elseScp = symTab.exitScope();
                }
                check(END);
                IRBlock[] elifs = new IRBlock[elifBlocks.size()];
                for (int i = 0; i < elifs.length; i++) {
                    elifs[i] = new IRBlock(elifPoses.get(i), elifBlocks.get(i).toArray(Node[]::new), elifScps.get(i).getLocalsCount(), elifScps.get(i).getClosableCount());
                }
                statement = new IfNode(_pos, condition, new IRBlock(thenBPos, thenBlock.toArray(Node[]::new), thenScp.getLocalsCount(), thenScp.getClosableCount()),
                        elifConds.toArray(Node[]::new), elifs,
                        elseBlock == null ? null : new IRBlock(elsePos, elseBlock.toArray(Node[]::new), elseScp.getLocalsCount(), elseScp.getClosableCount()));
            }
            case FOR -> {
                scan();
                symTab.labelNotLast();
                check(IDENT);
                Token ctrlToken = cur;
                if (ltok == ASSIGN) {
                    Position cvarPos = cur.pos();
                    scan();
                    // numerical for
                    symTab.enterPlainScope(true);
                    Position setupPos = cur.pos();
                    SpecificVarInfo internalControlVar = defineInternal("$internalControlVar$");
                    // start
                    Node initialValue = Exp();
                    check(COMMA);
                    // end
                    Node upperBound = Exp();
                    Position ubPos = la.pos();
                    SpecificVarInfo ubVar = defineInternal("$upperBound$");
                    Node step;
                    SpecificVarInfo stepVar;
                    //noinspection IfStatementWithIdenticalBranches
                    if (ltok == COMMA) {
                        scan();
                        // step
                        step = Exp();
                        stepVar = defineInternal("$step$");
                    } else {
                        // default step width is 1
                        step = ConstantNode.ofLong(ubPos, 1);
                        stepVar = defineInternal("$step$");
                    }
                    symTab.enterLoopScope();
                    SpecificVarInfo controlVar = define(ctrlToken, 0);
                    check(DO);
                    Position bpos = cur.pos();
                    var innerStats = Block();
                    VarScope innerLoopScp = symTab.exitScope();
                    VarScope outerLoopScp = symTab.exitScope();
                    check(END);

                    // we need to move the value of the internal iterator to the actual local iterator variable
                    innerStats.add(0, new AssignmentNode(bpos, new Node[]{new LocalAccessNode(cvarPos, controlVar)},
                            new Node[]{new LocalAccessNode(setupPos, internalControlVar)}));
                    // we add the for-step to the end of the internal statements
                    // the step might break the loop if an integer addition over/under-flows and must therefore close stuff in that case
                    innerStats.add(new StepForNode(cur.pos(), internalControlVar, stepVar, innerLoopScp.getClosableCount()));

                    Node entryCondition = new LogicBinaryOpNode(setupPos, true,
                            new LogicBinaryOpNode(setupPos, false,
                                    new RelationalOpNode(setupPos, LT.metatableFuncNameBinary, false,
                                            ConstantNode.ofLong(setupPos, 0),
                                            new LocalAccessNode(setupPos, stepVar)),
                                    new RelationalOpNode(setupPos, LE.metatableFuncNameBinary, false,
                                            new LocalAccessNode(setupPos, internalControlVar), new LocalAccessNode(ubPos, ubVar))
                            ),
                            new LogicBinaryOpNode(setupPos, false,
                                    new RelationalOpNode(setupPos, LT.metatableFuncNameBinary, true,
                                            ConstantNode.ofLong(setupPos, 0),
                                            new LocalAccessNode(setupPos, stepVar)),
                                    new RelationalOpNode(setupPos, LE.metatableFuncNameBinary, true,
                                            new LocalAccessNode(setupPos, internalControlVar), new LocalAccessNode(ubPos, ubVar))
                            ));

                    // build for-loop
                    statement = new DoEndNode(_pos, new Node[]{
                            new AssignmentNode(setupPos, new Node[]{new LocalAccessNode(setupPos, internalControlVar),
                                    new LocalAccessNode(setupPos, ubVar), new LocalAccessNode(setupPos, stepVar)},
                                    new Node[]{initialValue, upperBound, step}),
                            new CoerceNumericForNode(cur.pos(), internalControlVar, ubVar, stepVar),
                            new IfNode(_pos, entryCondition, new IRBlock(bpos, innerStats.toArray(Node[]::new), entryCondition, true,
                                    innerLoopScp.getLocalsCount(), innerLoopScp.getClosableCount()))},
                            outerLoopScp.getPrevFunctionLocalsCount(), outerLoopScp.getLocalsCount(), outerLoopScp.getClosableCount());
                } else {
                    // foreach
                    var ltkList = new ArrayList<Token>();
                    ltkList.add(ctrlToken);
                    while (ltok == COMMA) {
                        scan();
                        check(IDENT);
                        ltkList.add(cur);
                    }
                    check(IN);
                    symTab.enterPlainScope(true);
                    Position setupPos = cur.pos();
                    SpecificVarInfo internalControlVar = defineInternal("$internalControlVar$");
                    SpecificVarInfo itrFunc = defineInternal("$iteratorFunction$");
                    SpecificVarInfo state = defineInternal("$state$");
                    SpecificVarInfo closing = defineInternal("$closingVariable$", 3);
                    Node[] initials = ExpList();
                    Node setup = new AssignmentNode(setupPos, new Node[]{new LocalAccessNode(setupPos, itrFunc), new LocalAccessNode(setupPos, state),
                            new LocalAccessNode(setupPos, internalControlVar), new LocalAccessNode(setupPos, closing)}, initials);
                    check(DO);
                    final Position innerSetup = cur.pos();
                    symTab.enterLoopScope();
                    // the first one of these is the control variable accessible to lua
                    SpecificVarInfo[] forVals = ltkList.stream().map(t -> define(t, 0)).toArray(SpecificVarInfo[]::new);
                    Node step = new SequenceNode(innerSetup,
                            new AssignmentNode(innerSetup,
                                    Arrays.stream(forVals).map(v -> new LocalAccessNode(innerSetup, v)).toArray(LocalAccessNode[]::new),
                                    new Node[]{new FunctionCallNode(innerSetup,
                                            null, null, new LocalAccessNode(innerSetup, itrFunc), new Node[]{new LocalAccessNode(innerSetup, state),
                                            new LocalAccessNode(innerSetup, internalControlVar)})}),
                            new AssignmentNode(innerSetup,
                                    new Node[]{new LocalAccessNode(innerSetup, internalControlVar)},
                                    new Node[]{new LocalAccessNode(innerSetup, forVals[0])}));
                    Node entryCondition = new EqualsNode(innerSetup, new LocalAccessNode(innerSetup, internalControlVar), ConstantNode.ofNil(innerSetup));
                    Position bpos = cur.pos();
                    ArrayList<Node> inners = Block();
                    inners.add(step);
                    VarScope innerLoopScope = symTab.exitScope();
                    IRBlock block = new IRBlock(bpos, inners.toArray(Node[]::new), entryCondition, false, innerLoopScope.getLocalsCount(), innerLoopScope.getClosableCount());
                    VarScope outerClosableCnt = symTab.exitScope();
                    statement = new DoEndNode(_pos, new Node[]{setup, step, new IfNode(_pos, new LogicNotNode(innerSetup, entryCondition), block)},
                            outerClosableCnt.getPrevFunctionLocalsCount(), outerClosableCnt.getLocalsCount(), outerClosableCnt.getClosableCount());
                    check(END);
                }
            }
            case FUNCTION -> {
                scan();
                symTab.labelNotLast();
                check(IDENT);
                Position apos = cur.pos();
                Node access = genAccess(cur.pos(), symTab.get(cur.stVal()), cur.stVal());
                while (ltok == DOT) {
                    scan();
                    check(IDENT);
                    access = new DeRefNode(cur.pos(), access, ConstantNode.ofIdent(cur));
                }
                boolean hasSelf = false;
                if (ltok == COLON) {
                    scan();
                    hasSelf = true;
                    check(IDENT);
                    access = new DeRefNode(cur.pos(), access, ConstantNode.ofIdent(cur));
                }
                if (access instanceof LocalAccessNode acNode) {
                    var acInfo = acNode.info.baseInfo();
                    if (acInfo.isConstant()) {
                        throw new LuaSemanticException(cur.pos(), "Constant variable must not be written");
                    }
                    acInfo.setWritten();
                }
                var func = FuncBody(hasSelf);
                statement = new AssignmentNode(apos, new Node[]{access}, new Node[]{func});
            }
            case LOCAL -> {
                scan();
                symTab.labelNotLast();
                if (ltok == FUNCTION) {
                    scan();
                    check(IDENT);
                    Position tpos = cur.pos();
                    SpecificVarInfo target = define(cur, 0);
                    var definition = new AssignmentNode(tpos, new Node[]{new LocalAccessNode(tpos, target)}, new Node[]{ConstantNode.ofNil(tpos)});
                    target.baseInfo().setWritten();
                    var func = FuncBody(false);
                    statement = new SequenceNode(tpos, definition, new AssignmentNode(tpos, new Node[]{new LocalAccessNode(tpos, target)}, new Node[]{func}));
                } else {
                    check(IDENT);
                    var localList = new ArrayList<Tuple<Token, Integer>>();
                    Token locVar = cur;
                    Position apos = cur.pos();
                    int attributes = Attrib();
                    localList.add(new Tuple<>(locVar, attributes));
                    while (ltok == COMMA) {
                        scan();
                        check(IDENT);
                        locVar = cur;
                        attributes = Attrib();
                        localList.add(new Tuple<>(locVar, attributes));
                    }
                    Node[] expressions;
                    if (ltok == ASSIGN) {
                        scan();
                        apos = cur.pos();
                        expressions = ExpList();
                    } else {
                        expressions = new Node[0];
                    }
                    statement = new AssignmentNode(apos, localList.stream().map(t -> new LocalAccessNode(t.x().pos(), define(t.x(), t.y()))).toArray(Node[]::new), expressions);
                }
            }
            case IDENT, LPAR -> {
                symTab.labelNotLast();
                statement = StatExp();
            }
            default -> throw new InternalLuaLoadingError("unexpected statement start '%s'".formatted(ltok.name()));
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

    @SuppressWarnings("unused")
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
            result = genAccess(cur.pos(), info, cur.stVal());
            onlyIdent = true;
        }
        loop:
        for (; ; ) {
            switch (ltok) {
                case LBRAK, DOT -> result = DeRef(result);
                case COLON, LPAR, LITERAL_STRING, LBRAC -> result = FuncCall(result);
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
                var packedInfo = OopValExp();
                assignTargets.add(packedInfo.x());
                qLocals.add(new Tuple<>(packedInfo.y(), packedInfo.z()));
                if (!isAssignable()) {
                    throw new LuaParserException(la.pos(), "Expected <%s>, got <%s>".formatted(DOT.rep, ltok.rep));
                }
            }
            check(ASSIGN);
            Position apos = cur.pos();
            qLocals.forEach(p -> {
                if (p.x() != null && p.y()) {
                    if (p.x().baseInfo().isConstant()) {
                        throw new LuaSemanticException(cur.pos(), "Constant variable must not be written");
                    }
                    p.x().baseInfo().setWritten();
                }
            });
            Node[] expressions = ExpList();
            result = new AssignmentNode(apos, assignTargets.toArray(Node[]::new), expressions);
        } else {
            if (result instanceof FunctionCallNode f) {
                result = new FunctionStatementNode(f.sourcePos, f);
            } else {
                throw new LuaParserException(cur.pos(), "(At %s) Expected <%s>, got <%s>".formatted(cur.pos(), EQ.rep, cur.type().rep));
            }
        }
        return result;
    }

    private Triple<Node, SpecificVarInfo, Boolean> OopValExp() {
        if (OOP_CALLABLE_LITERAL_START.contains(ltok)) {
            Node result = OopCallableLiteral();
            if (ltok == COLON) {
                result = OopFuncCall(result);
                loop:
                for (; ; ) {
                    switch (ltok) {
                        case LBRAK, DOT -> result = DeRef(result);
                        case COLON, LPAR, LITERAL_STRING, LBRAC -> result = FuncCall(result);
                        default -> {
                            break loop;
                        }
                    }
                }
            }
            return new Triple<>(result, null, false);
        } else {
            return ValExp();
        }
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
            result = genAccess(cur.pos(), info, cur.stVal());
            onlyIdent = true;
        }
        loop:
        for (; ; ) {
            switch (ltok) {
                case LBRAK, DOT -> result = DeRef(result);
                case COLON, LPAR, LITERAL_STRING, LBRAC -> result = FuncCall(result);
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
            index = ConstantNode.ofIdent(cur);
        }
        return new DeRefNode(target.sourcePos, target, index);
    }

    private Node FuncCall(Node callable) {
        Node object = null;
        Node func = callable;
        if (ltok == COLON) {
            scan();
            object = callable;
            // code gen in FunctionCallNode relies on this being a constant ident
            check(IDENT);
            func = ConstantNode.ofIdent(cur);
        }
        Position pos = la.pos();
        Node[] args = Args();
        return new FunctionCallNode(pos, object, getEnv(pos), func, args);
    }

    private Node OopFuncCall(Node callable) {
        check(COLON);
        Node object = callable;
        // code gen in FunctionCallNode relies on this being a constant ident
        check(IDENT);
        Node func = ConstantNode.ofIdent(cur);
        Position pos = la.pos();
        Node[] args = Args();
        return new FunctionCallNode(pos, object, getEnv(pos), func, args);
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
            scan();
            Position pos = cur.pos();
            Node y = BinOp1();
            result = new LogicBinaryOpNode(pos, true, result, y);
        }
        return result;
    }

    private Node BinOp1() {
        // and
        Node result = BinOp2();
        while (ltok == AND) {
            scan();
            Position pos = cur.pos();
            Node y = BinOp2();
            result = new LogicBinaryOpNode(pos, false, result, y);
        }
        return result;
    }

    private Node BinOp2() {
        // < > <= >= ~= ==
        Node result = BinOp3();
        loop:
        for (; ; ) {
            Position opPos = la.pos();
            switch (ltok) {
                case LT, GT -> {
                    var op = ltok;
                    scan();
                    Node y = BinOp3();
                    result = new RelationalOpNode(opPos, LT.metatableFuncNameBinary, op != LT, result, y);
                }
                case LE, GE -> {
                    var op = ltok;
                    scan();
                    Node y = BinOp3();
                    result = new RelationalOpNode(opPos, LE.metatableFuncNameBinary, op != LE, result, y);
                }
                case EQ, NE -> {
                    var op = ltok;
                    scan();
                    Node y = BinOp3();
                    result = new EqualsNode(opPos, result, y);
                    if (op == NE) {
                        result = new LogicNotNode(opPos, result);
                    }
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
            Position pos = cur.pos();
            Node y = BinOp4();
            result = BinaryOpNode.bitwise(pos, op.metatableFuncNameBinary, result, y);
        }
        return result;
    }

    private Node BinOp4() {
        // ~
        var result = BinOp5();
        while (ltok == BXOR) {
            var op = ltok;
            scan();
            Position pos = cur.pos();
            Node y = BinOp5();
            result = BinaryOpNode.bitwise(pos, op.metatableFuncNameBinary, result, y);
        }
        return result;
    }

    private Node BinOp5() {
        // &
        var result = BinOp6();
        while (ltok == BAND) {
            var op = ltok;
            scan();
            Position pos = cur.pos();
            Node y = BinOp6();
            result = BinaryOpNode.bitwise(pos, op.metatableFuncNameBinary, result, y);
        }
        return result;
    }

    private Node BinOp6() {
        // << >>
        var result = BinOp7();
        while (ltok == SHL || ltok == SHR) {
            var op = ltok;
            scan();
            Position pos = cur.pos();
            Node y = BinOp7();
            result = BinaryOpNode.bitwise(pos, op.metatableFuncNameBinary, result, y);
        }
        return result;
    }

    private Node BinOp7() {
        // ..
        var result = BinOp8();
        while (ltok == DDOT) {
            scan();
            Position pos = cur.pos();
            Node y = BinOp8();
            result = BinaryOpNode.stringConcat(pos, result, y);
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
                Position pos = cur.pos();
                Node y = BinOp9();
                result = BinaryOpNode.arithmetic(pos, op.metatableFuncNameBinary, result, y);
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
                    Position pos = cur.pos();
                    Node y = UnOp();
                    result = BinaryOpNode.arithmetic(pos, op.metatableFuncNameBinary, result, y);
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
        var opList = new Stack<Tuple<TokenType, Position>>();
        loop:
        for (; ; ) {
            switch (ltok) {
                case NOT, HASH, SUB, BXOR -> {
                    opList.push(new Tuple<>(ltok, la.pos()));
                    scan();
                }
                default -> {
                    break loop;
                }
            }
        }
        Node result = BinOp10();
        while (!opList.isEmpty()) {
            var op = opList.pop();
            result = switch (op.x()) {
                case NOT -> new LogicNotNode(op.y(), result);
                case HASH -> new LengthOfNode(op.y(), result);
                case SUB -> UnaryOpNode.negate(op.y(), result);
                case BXOR -> UnaryOpNode.invert(op.y(), result);
                default -> throw new InternalLuaLoadingError("should not reach");
            };
        }
        return result;
    }

    private Node BinOp10() {
        // ^
        Node result = TermExp();
        while (ltok == EXPONENT) {
            var op = ltok;
            scan();
            Position pos = cur.pos();
            Node y = TermExp();
            result = BinaryOpNode.arithmetic(pos, op.metatableFuncNameBinary, result, y);
        }
        return result;
    }

    private Node TermExp() {
        // constants, funcdef or ValExp
        switch (ltok) {
            case TDOT -> {
                scan();
                if (!symTab.paramsDefined()) {
                    throw new LuaSemanticException(cur.pos(), "cannot use '...' outside a vararg function");
                }
                return new LocalAccessNode(cur.pos(), symTab.get("..."));
            }
            case FUNCTION -> {
                scan();
                return FuncBody(false);
            }
            default -> {
                return OopValExp().x();
            }
        }
    }

    private static final EnumSet<TokenType> OOP_CALLABLE_LITERAL_START = EnumSet.of(NIL, TRUE, FALSE, NUMERAL, LITERAL_STRING, LBRAC);

    private Node OopCallableLiteral() {
        switch (ltok) {
            case NIL -> {
                scan();
                return ConstantNode.ofNil(cur.pos());
            }
            case TRUE, FALSE -> {
                scan();
                return ConstantNode.ofBool(cur.pos(), cur.type() == TRUE);
            }
            case NUMERAL -> {
                scan();
                return cur.nVal() < 0 ? ConstantNode.ofLong(cur) : ConstantNode.ofDouble(cur);
            }
            case LITERAL_STRING -> {
                scan();
                return ConstantNode.ofB64(cur);
            }
            case LBRAC -> {
                return TableConstructor();
            }
            default -> {
                throw new InternalLuaLoadingError("should not reach");
            }
        }
    }

    private FunctionDefinitionNode FuncBody(boolean hasSelf) {
        Position capPos = cur.pos();
        check(LPAR);
        Token selfPlaceholder = null;
        if (hasSelf) {
            selfPlaceholder = new Token(IDENT, cur.pos(), "self");
        }
        ArrayList<Token> ps;
        if (ltok == TDOT || ltok == IDENT) {
            ps = ParList();
        } else {
            ps = new ArrayList<>();
        }
        if (selfPlaceholder != null) {
            ps.add(0, selfPlaceholder);
        }
        check(RPAR);
        Position _pos = la.pos();
        boolean hasParamsArg = !ps.isEmpty() && ps.get(ps.size() - 1).type() == TDOT;
        symTab.enterFunctionScope(hasParamsArg);
        SpecificVarInfo[] args = ps.stream().map(p -> define(p, 0)).toArray(SpecificVarInfo[]::new);
        var innerStats = Block();
        int maxLocalCnt = symTab.getMaxFuncLocals();
        VarScope scp = symTab.exitScope();
        check(END);
        return new FunctionDefinitionNode(capPos, scp.captured.keySet().stream().map(info -> new PossiblyBoxedLocalAccessNode(capPos, info)).toArray(PossiblyBoxedLocalAccessNode[]::new),
                new IRFunction(_pos, innerStats.toArray(Node[]::new), scp.getLocalsCount(), scp.getClosableCount(), maxLocalCnt, args.length, hasParamsArg));
    }

    private ArrayList<Token> ParList() {
        var parameters = new ArrayList<Token>();
        if (ltok == TDOT) {
            scan();
            parameters.add(cur);
        } else {
            check(IDENT);
            parameters.add(cur);
            while (ltok == COMMA && lla.type() != TDOT) {
                scan();
                check(IDENT);
                parameters.add(cur);
            }
            if (ltok == COMMA) {
                scan();
                check(TDOT);
                parameters.add(cur);
            }
        }
        return parameters;
    }

    @SuppressWarnings("unused")
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
            result = new Node[]{ConstantNode.ofB64(cur)};
        } else {
            result = new Node[]{TableConstructor()};
        }
        return result;
    }

    private Node TableConstructor() {
        check(LBRAC);
        Position tpos = cur.pos();
        Node[] keyVals;
        if (ltok == RBRAC) {
            scan();
            keyVals = new Node[0];
        } else {
            keyVals = FieldList();
        }
        return new ConstructedTableNode(tpos, keyVals);
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
            fieldList.add(ConstantNode.ofIdent(cur));
            scan();  // ASSIGN
            fieldList.add(Exp());
        } else {
            fieldList.add(ConstantNode.ofNull(cur.pos()));
            fieldList.add(Exp());
        }
    }

    private void FieldSep() {
        if (ltok == COMMA || ltok == SEMICOLON) {
            scan();
        }
    }
}
