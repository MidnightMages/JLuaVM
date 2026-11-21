package dev.asdf00.jluavm.parsing;

import dev.asdf00.jluavm.exceptions.loading.LuaParserException;
import dev.asdf00.jluavm.exceptions.loading.LuaSemanticException;
import dev.asdf00.jluavm.parsing.container.LocalVariable;
import dev.asdf00.jluavm.parsing.container.LocalVariable.LocalVariableAccessor;
import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.container.Token;
import dev.asdf00.jluavm.parsing.container.TokenType;
import dev.asdf00.jluavm.parsing.ir.CFGBuilder;
import dev.asdf00.jluavm.parsing.ir.IRElement;
import dev.asdf00.jluavm.parsing.ir.IRElement.*;
import dev.asdf00.jluavm.runtime.utils.Singletons;
import dev.asdf00.jluavm.parsing.ir.ControlFlowGraph;
import dev.asdf00.jluavm.parsing.ir.JumpLabel;
import dev.asdf00.jluavm.utils.Tuple;

import java.util.*;

import static dev.asdf00.jluavm.parsing.Lexer.STARTING_POS;
import static dev.asdf00.jluavm.parsing.container.TokenType.*;

public final class Parser {
    private final SymbolTable symTab;
    private final Lexer lexer;
    private CFGBuilder cb;
    private TokenType ltok = EOF;  // TokenType of la
    private Token cur;  // current token
    private Token la;  // lookahead token
    private Token lla;  // lookahead token 2

    public Parser(String input) {
        symTab = new SymbolTable();
        lexer = new Lexer(input);
    }

    /*-
     * =================================================================================================================
     *           UTIL METHODS        UTIL METHODS        UTIL METHODS        UTIL METHODS        UTIL METHODS
     * =================================================================================================================
     */

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

    private IRElement genAccess(Position pos, LocalVariableAccessor accessor, String ident) {
        if (accessor != null) {
            return new LocalAccessNode(pos, accessor);
        }
        if ("_ENV".equals(ident)) {
            return new EnvAccessNode(pos);
        }
        return new IndexedAccessNode(pos, getEnv(pos), ConstantNode.ofIdent(pos, ident));
    }

    private IRElement getEnv(Position pos) {
        if (symTab.isEnvRedeclared()) {
            return new LocalAccessNode(pos, symTab.findLocal("_ENV"));
        } else {
            return new EnvAccessNode(pos);
        }
    }

    /**
     * Emits the initial assignment of freshly declared locals, of which at most one may be to-be-closed. The value of
     * the to-be-closed variable is checked for closability right after the assignment.
     */
    private void genAssignLocals(Position apos, LocalAccessNode[] targets, IRElement[] expressions) {
        cb.statement(new AssignmentNode(apos, targets, expressions));
        if (expressions.length == 0) {
            // all locals are nil, which is closable
            return;
        }
        for (var target : targets) {
            if (target.local.getVariable().isClosable) {
                cb.statement(new CheckClosableNode(target.pos, target.local));
            }
        }
    }

    /*-
     * =================================================================================================================
     *    PARSING   PARSING   PARSING   PARSING   PARSING   PARSING   PARSING   PARSING   PARSING   PARSING   PARSING
     * =================================================================================================================
     */

    public FunctionDefNode parse() throws LuaParserException {
        cur = null;
        la = lexer.next();
        lla = lexer.next();
        ltok = la.type();
        return Chunk();
    }

    private FunctionDefNode Chunk() {
        var outerCb = cb;
        cb = new CFGBuilder(true);
        symTab.openFunctionScope(true);
        Block();
        var closed = symTab.closeFunctionScope(cb);
        assert closed.captures().length == 0 : "chunk must not capture anything";
        check(EOF);
        ControlFlowGraph cfg = cb.finish(cur.pos());
        cb = outerCb;
        return new FunctionDefNode(STARTING_POS, true, Singletons.EMPTY_LOCAL_ACCESSOR_ARRAY, closed.arguments(), cfg);
    }

    private void Block() {
        while (STAT_START.contains(ltok)) {
            Stat();
        }
        if (ltok == RETURN) {
            symTab.labelNotLast(cb);
            scan();
            Position pos = cur.pos();
            IRElement[] returnValues;
            if (EXP_START.contains(ltok)) {
                returnValues = ExpList();
            } else {
                returnValues = new IRElement[0];
            }
            if (ltok == SEMICOLON) {
                scan();
            }
            // TODO check for tailcalls on the last return value
            cb.ret(pos, returnValues, symTab.closablesInFunction());
        }
    }

    private static final EnumSet<TokenType> STAT_START = EnumSet.of(SEMICOLON, IDENT, LPAR, DCOLON, BREAK, GOTO, DO, WHILE, REPEAT, IF, FOR, FUNCTION, LOCAL,
            NIL, FALSE, TRUE, NUMERAL, LITERAL_STRING, LBRAC);

    private void Stat() {
        Position _pos = la.pos();
        if (ltok != SEMICOLON && ltok != DCOLON) {
            // labels directly followed by this statement are not at the end of their block
            symTab.labelNotLast(cb);
        }
        switch (ltok) {
            case SEMICOLON -> {
                // nop
                scan();
            }
            case BREAK -> {
                scan();
                if (!symTab.isInsideLoop()) {
                    throw new LuaParserException(_pos, "'break' is not inside a loop");
                }
                cb.jump(symTab.resolveBreak(), symTab.closablesUntilLoop());
            }
            case GOTO -> {
                scan();
                check(IDENT);
                symTab.resolveJump(cb, cur.stVal(), cur.pos());
            }
            case DCOLON -> {
                // label
                scan();
                check(IDENT);
                symTab.declareJumpLabel(cur.stVal(), cur.pos());
                check(DCOLON);
            }
            case DO -> {
                scan();
                symTab.openBlockScope();
                Block();
                symTab.closeScope(cb);
                check(END);
            }
            case WHILE -> {
                scan();
                var topLabel = new JumpLabel(_pos);
                cb.label(topLabel);
                IRElement entryCond = Exp();
                check(DO);

                cb.branch(entryCond);
                cb.enterTrue();
                symTab.openLoopScope();
                Block();
                JumpLabel breakLabel = symTab.closeLoopScope(cb);
                cb.jump(topLabel);
                cb.exitBranch();
                cb.label(breakLabel);

                check(END);
            }
            case REPEAT -> {
                scan();
                var topLabel = new JumpLabel(_pos);
                cb.label(topLabel);

                symTab.openLoopScope();
                Block();
                check(UNTIL);
                // the exit condition follows any label at the end of the loop body
                symTab.labelNotLast(cb);
                IRElement exitCond = Exp();

                // variables of the loop body are still visible in the exit condition, so the loop is left like a break
                cb.branch(exitCond);
                cb.enterTrue();
                cb.jump(symTab.resolveBreak(), symTab.closablesUntilLoop());
                cb.exitBranch();
                JumpLabel breakLabel = symTab.closeLoopScope(cb);
                cb.jump(topLabel);
                cb.label(breakLabel);
            }
            case IF -> {
                scan();
                IRElement condition = Exp();
                check(THEN);
                cb.branch(condition);
                cb.enterTrue();
                symTab.openBlockScope();
                Block();
                symTab.closeScope(cb);
                int openBranches = 1;
                while (ltok == ELSEIF) {
                    scan();
                    IRElement elifCond = Exp();
                    check(THEN);
                    cb.enterFalse();
                    cb.branch(elifCond);
                    openBranches++;
                    cb.enterTrue();
                    symTab.openBlockScope();
                    Block();
                    symTab.closeScope(cb);
                }
                if (ltok == ELSE) {
                    scan();
                    cb.enterFalse();
                    symTab.openBlockScope();
                    Block();
                    symTab.closeScope(cb);
                }
                check(END);
                for (int i = 0; i < openBranches; i++) {
                    cb.exitBranch();
                }
            }
            case FOR -> {
                scan();
                check(IDENT);
                Token ctrlToken = cur;
                if (ltok == ASSIGN) {
                    scan();
                    // numerical for
                    symTab.openBlockScope();
                    LocalVariableAccessor _internalControl = symTab.declareLocal("$internalControl$");
                    // start
                    IRElement initialValue = Exp();
                    check(COMMA);
                    // end
                    IRElement upperBound = Exp();
                    Position ubPos = la.pos();
                    LocalVariableAccessor _upperBound = symTab.declareLocal("$upperBound$");
                    IRElement step;
                    LocalVariableAccessor _step = symTab.declareLocal("$step$");
                    if (ltok == COMMA) {
                        scan();
                        // step
                        step = Exp();
                    } else {
                        // default step width is 1
                        step = ConstantNode.ofInteger(ubPos, 1);
                    }

                    /*-
                    We use Lua 5.4 semantics here. This will be constructed as:
                        do
                          local _internalControl = tonumber(initialValue)
                          local _upperBound = tonumber(upperBound)
                          local _step = tonumber(step)
                          if _step == 0 then
                            error("'for' step is zero")
                          end
                          if 0 < _step and _internalControl <= _upperBound or _step < 0 and _upperBound <= _internalControl then
                            ::body::
                            local ctrlVar = _internalControl
                            -- BODY
                            if stepFor(_internalControl, _upperBound, _step) then
                              goto body
                            end
                          end
                          ::break::
                        end
                    'stepFor' advances _internalControl and fails on overflow or when passing _upperBound.
                     */

                    cb.statement(new LocalDeclarationNode(_pos, _internalControl));
                    cb.statement(new LocalDeclarationNode(_pos, _upperBound));
                    cb.statement(new LocalDeclarationNode(_pos, _step));
                    cb.statement(new AssignmentNode(_pos,
                            new LocalAccessNode(_pos, _internalControl),
                            new ToNumberOrFailNode(_pos, initialValue, "Bad 'for' initial value (number expected, got %s)")));
                    cb.statement(new AssignmentNode(_pos,
                            new LocalAccessNode(_pos, _upperBound),
                            new ToNumberOrFailNode(_pos, upperBound, "Bad 'for' limit (number expected, got %s)")));
                    cb.statement(new AssignmentNode(_pos,
                            new LocalAccessNode(_pos, _step),
                            new ToNumberOrFailNode(_pos, step, "Bad 'for' step (number expected, got %s)")));
                    cb.branch(new BinaryOperationNode(_pos, EQ,
                            new LocalAccessNode(_pos, _step),
                            ConstantNode.ofInteger(_pos, 0)));
                    cb.enterTrue();
                    cb.statement(new ErrorNode(_pos, ConstantNode.ofString(_pos, "'for' step is zero")));
                    cb.exitBranch();
                    cb.branch(new OrNode(_pos,
                            new AndNode(_pos,
                                    new BinaryOperationNode(_pos, LT,
                                            ConstantNode.ofInteger(_pos, 0),
                                            new LocalAccessNode(_pos, _step)),
                                    new BinaryOperationNode(_pos, LE,
                                            new LocalAccessNode(_pos, _internalControl),
                                            new LocalAccessNode(_pos, _upperBound))),
                            new AndNode(_pos,
                                    new BinaryOperationNode(_pos, LT,
                                            new LocalAccessNode(_pos, _step),
                                            ConstantNode.ofInteger(_pos, 0)),
                                    new BinaryOperationNode(_pos, LE,
                                            new LocalAccessNode(_pos, _upperBound),
                                            new LocalAccessNode(_pos, _internalControl)))));
                    cb.enterTrue();
                    var bodyLabel = new JumpLabel(_pos);
                    cb.label(bodyLabel);

                    symTab.openLoopScope();
                    Position ctrlPos = ctrlToken.pos();
                    LocalVariableAccessor actualCtrlVar = symTab.declareLocal(ctrlToken.stVal(), LocalVariable.Attributes.NONE);
                    cb.statement(new LocalDeclarationNode(ctrlPos, actualCtrlVar));
                    cb.statement(new AssignmentNode(ctrlPos, new LocalAccessNode(ctrlPos, actualCtrlVar),
                            new LocalAccessNode(ctrlPos, _internalControl)));
                    check(DO);
                    Block();
                    JumpLabel breakLabel = symTab.closeLoopScope(cb);
                    check(END);

                    cb.branch(new ForStepNode(cur.pos(), _internalControl, _upperBound, _step));
                    cb.enterTrue();
                    cb.jump(bodyLabel);
                    cb.exitBranch();
                    cb.exitBranch();
                    cb.label(breakLabel);
                    symTab.closeScope(cb); // close scope of internal variables
                } else {
                    // foreach
                    var loopVarTokens = new ArrayList<Token>();
                    loopVarTokens.add(ctrlToken);
                    while (ltok == COMMA) {
                        scan();
                        check(IDENT);
                        loopVarTokens.add(cur);
                    }
                    check(IN);
                    symTab.openBlockScope();
                    LocalVariableAccessor internalControlVar = symTab.declareLocal("$internalControlVar$");
                    LocalVariableAccessor itrFunc = symTab.declareLocal("$iteratorFunction$");
                    LocalVariableAccessor state = symTab.declareLocal("$state$");
                    LocalVariableAccessor closing = symTab.declareLocal("$closingVariable$",
                            LocalVariable.Attributes.CONSTANT | LocalVariable.Attributes.CLOSABLE);
                    IRElement[] initials = ExpList();
                    check(DO);
                    final Position innerSetupPos = cur.pos();

                    /*-
                    This gets expanded to:
                        do
                          local itrFunc, state, internalControlVar, closing<close> = initials  -- as a multi assignment
                          ::top::
                          local var1, var2, ..., varN
                          internalControlVar, var2, ..., varN = itrFunc(state, internalControlVar)
                          if internalControlVar == nil then
                            goto break
                          end
                          var1 = internalControlVar
                          -- BODY
                          goto top
                          ::break::
                        end
                     */

                    cb.statement(new LocalDeclarationNode(_pos, itrFunc));
                    cb.statement(new LocalDeclarationNode(_pos, state));
                    cb.statement(new LocalDeclarationNode(_pos, internalControlVar));
                    cb.statement(new LocalDeclarationNode(_pos, closing));
                    genAssignLocals(_pos, new LocalAccessNode[]{
                                    new LocalAccessNode(_pos, itrFunc),
                                    new LocalAccessNode(_pos, state),
                                    new LocalAccessNode(_pos, internalControlVar),
                                    new LocalAccessNode(_pos, closing)
                            },
                            initials);
                    var topLabel = new JumpLabel(innerSetupPos);
                    cb.label(topLabel);

                    symTab.openLoopScope();
                    // the first one of these is the control variable accessible to lua
                    LocalVariableAccessor[] loopVars = loopVarTokens.stream().map(t -> symTab.declareLocal(t.stVal())).toArray(LocalVariableAccessor[]::new);
                    var inLoopSetupAccesses = new IRElement[loopVars.length];
                    for (int i = 0; i < loopVars.length; i++) {
                        cb.statement(new LocalDeclarationNode(innerSetupPos, loopVars[i]));
                        inLoopSetupAccesses[i] = new LocalAccessNode(innerSetupPos, loopVars[i]);
                    }
                    inLoopSetupAccesses[0] = new LocalAccessNode(innerSetupPos, internalControlVar);
                    cb.statement(new AssignmentNode(innerSetupPos,
                            inLoopSetupAccesses,
                            new FunctionCallNode(innerSetupPos,
                                    new LocalAccessNode(innerSetupPos, itrFunc),
                                    new LocalAccessNode(innerSetupPos, state),
                                    new LocalAccessNode(innerSetupPos, internalControlVar))));
                    cb.branch(new BinaryOperationNode(innerSetupPos, EQ,
                            new LocalAccessNode(innerSetupPos, internalControlVar),
                            ConstantNode.ofNil(innerSetupPos)));
                    cb.enterTrue();
                    cb.jump(symTab.resolveBreak(), symTab.closablesUntilLoop());
                    cb.exitBranch();
                    cb.statement(new AssignmentNode(innerSetupPos,
                            new LocalAccessNode(innerSetupPos, loopVars[0]),
                            new LocalAccessNode(innerSetupPos, internalControlVar)));

                    Block();
                    JumpLabel breakLabel = symTab.closeLoopScope(cb);
                    check(END);
                    cb.jump(topLabel);
                    cb.label(breakLabel);
                    symTab.closeScope(cb); // hidden vars scope
                }
            }
            case FUNCTION -> {
                scan();
                check(IDENT);
                Position apos = cur.pos();
                IRElement access = genAccess(cur.pos(), symTab.findLocal(cur.stVal()), cur.stVal());
                while (ltok == DOT) {
                    scan();
                    check(IDENT);
                    access = new IndexedAccessNode(cur.pos(), access, ConstantNode.of(cur));
                }
                boolean hasSelf = false;
                if (ltok == COLON) {
                    scan();
                    hasSelf = true;
                    check(IDENT);
                    access = new IndexedAccessNode(cur.pos(), access, ConstantNode.of(cur));
                }
                IRElement func = FuncBody(hasSelf);
                cb.statement(new AssignmentNode(apos, access, func));
            }
            case LOCAL -> {
                scan();
                if (ltok == FUNCTION) {
                    scan();
                    check(IDENT);
                    Position namePos = cur.pos();
                    LocalVariableAccessor funcVar = symTab.declareLocal(cur.stVal());
                    IRElement function = FuncBody(false);
                    // insert a fake write to correctly apply upvalue capturing semantics
                    funcVar.getVariable().setWritten(namePos);
                    cb.statement(new LocalDeclarationNode(namePos, funcVar));
                    cb.statement(new AssignmentNode(namePos, new LocalAccessNode(namePos, funcVar), function));
                } else {
                    check(IDENT);
                    var localList = new ArrayList<Tuple<Token, Integer>>();
                    Token locVar = cur;
                    int attributes = Attrib();
                    localList.add(new Tuple<>(locVar, attributes));
                    while (ltok == COMMA) {
                        scan();
                        check(IDENT);
                        locVar = cur;
                        attributes = Attrib();
                        localList.add(new Tuple<>(locVar, attributes));
                    }
                    IRElement[] expressions;
                    Position apos;
                    if (ltok == ASSIGN) {
                        scan();
                        apos = cur.pos();
                        expressions = ExpList();
                    } else {
                        // this is a pure declaration
                        apos = _pos;
                        expressions = new IRElement[0];
                    }
                    if (localList.stream().filter(l -> l.y() == (LocalVariable.Attributes.CONSTANT | LocalVariable.Attributes.CLOSABLE)).count() > 1) {
                        throw new LuaSemanticException(_pos, "multiple to-be-closed variables in local list");
                    }
                    var localAccesses = new LocalAccessNode[localList.size()];
                    for (int i = 0; i < localAccesses.length; i++) {
                        Tuple<Token, Integer> localDefinition = localList.get(i);
                        Position lPos = localDefinition.x().pos();
                        LocalVariableAccessor local = symTab.declareLocal(localDefinition.x().stVal(), localDefinition.y());
                        // create declarations and pseudo accesses
                        cb.statement(new LocalDeclarationNode(lPos, local));
                        localAccesses[i] = new LocalAccessNode(lPos, local);
                    }
                    genAssignLocals(apos, localAccesses, expressions);
                }
            }
            case IDENT, LPAR, NIL, FALSE, TRUE, NUMERAL, LITERAL_STRING, LBRAC -> cb.statement(StatExp());
            default -> throw new InternalLuaParsingError("unexpected statement start '%s'".formatted(ltok.name()));
        }
    }

    private int Attrib() {
        int rVal = LocalVariable.Attributes.NONE;
        if (ltok == LT) {
            scan();
            check(IDENT);
            if ("const".equals(cur.stVal())) {
                rVal = LocalVariable.Attributes.CONSTANT;
            } else if ("close".equals(cur.stVal())) {
                // closables are implicitly constant
                rVal = LocalVariable.Attributes.CONSTANT | LocalVariable.Attributes.CLOSABLE;
            } else {
                throw new LuaParserException(cur.pos(), "Only 'const' and 'close' are allowed as attributes, not '%s'!".formatted(cur.stVal()));
            }
            check(GT);
        }
        return rVal;
    }

    // private static final EnumSet<TokenType> DEREF_OR_FUNCCALL_START = EnumSet.of(LBRAK, DOT,
    // COLON, LPAR, LITERAL_STRING, LBRAC);

    private IRElement StatExp() {
        IRElement result;
        if (ltok == LPAR) {
            scan();
            result = Exp();
            // values in parentheses will be adjusted to 1
            result = new AdjustNumberOfValuesNode(1, result);
            check(RPAR);
            if (ltok == LBRAK || ltok == DOT) {
                result = DeRef(result);
            } else {
                result = FuncCall(result);
            }
        } else if (ltok == IDENT) {
            scan();
            result = genAccess(cur.pos(), symTab.findLocal(cur.stVal()), cur.stVal());
        } else {
            assert OOP_CALLABLE_LITERAL_START.contains(ltok) : "unallowed token " + ltok;
            scan();
            result = OopFuncCall(ConstantNode.of(cur));
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
        }
        if (isAssignable()) {
            var assignTargets = new ArrayList<IRElement>();
            assignTargets.add(result);
            while (ltok == COMMA) {
                scan();
                IRElement assignAccess = OopValExp();
                assignTargets.add(assignAccess);
                if (!isAssignable()) {
                    throw new LuaParserException(la.pos(), "Expected <%s>, got <%s>".formatted(DOT.rep, ltok.rep));
                }
            }
            check(ASSIGN);
            Position apos = cur.pos();
            IRElement[] expressions = ExpList();
            result = new AssignmentNode(apos, assignTargets.toArray(IRElement[]::new), expressions);
        } else {
            if (result instanceof AdjustNumberOfValuesNode adjustNode && (adjustNode.multivalue instanceof FunctionCallNode || adjustNode.multivalue instanceof OopCallNode)) {
                // if a function call is a statement, we drop the return values instead of pruning
                // them
                result = new AdjustNumberOfValuesNode(0, adjustNode.multivalue);
            } else {
                throw new LuaParserException(cur.pos(), "(At %s) Expected <%s>, got <%s>".formatted(cur.pos(), EQ.rep, cur.type().rep));
            }
        }
        return result;
    }

    private IRElement OopValExp() {
        if (OOP_CALLABLE_LITERAL_START.contains(ltok)) {
            IRElement result = OopCallableLiteral();
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
            return result;
        } else {
            return ValExp();
        }
    }

    private IRElement ValExp() {
        IRElement result;
        if (ltok == LPAR) {
            scan();
            result = Exp();
            // values in parentheses will be adjusted to 1
            result = new AdjustNumberOfValuesNode(1, result);
            check(RPAR);
        } else {
            check(IDENT);
            result = genAccess(cur.pos(), symTab.findLocal(cur.stVal()), cur.stVal());
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
        }
        return result;
    }

    private IRElement DeRef(IRElement target) {
        IRElement index;
        if (ltok == LBRAK) {
            scan();
            index = Exp();
            check(RBRAK);
        } else {
            check(DOT);
            check(IDENT);
            index = ConstantNode.of(cur);
        }
        return new IndexedAccessNode(target.pos, target, index);
    }

    private IRElement FuncCall(IRElement callable) {
        IRElement object = null;
        IRElement func = callable;
        if (ltok == COLON) {
            scan();
            object = callable;
            check(IDENT);
            func = ConstantNode.of(cur);
        }
        Position pos = la.pos();
        IRElement[] args = Args();
        // _ENV is passed for type extension functions on OOP calls
        // per default, return values are adjusted to 1; this may be stripped later
        return new AdjustNumberOfValuesNode(1, object == null
                ? new FunctionCallNode(pos, func, args)
                : new OopCallNode(pos, getEnv(pos), object, func, args));
    }

    private IRElement OopFuncCall(IRElement object) {
        check(COLON);
        // code gen in FunctionCallNode relies on this being a constant ident
        check(IDENT);
        IRElement callable = ConstantNode.of(cur);
        Position pos = la.pos();
        IRElement[] args = Args();
        // _ENV is passed for type extension functions on OOP calls
        return new AdjustNumberOfValuesNode(pos, 1,
                new OopCallNode(pos, getEnv(pos), object, callable, args));
    }

    private IRElement[] ExpList() {
        var expressions = new ArrayList<IRElement>();
        expressions.add(Exp());
        while (ltok == COMMA) {
            scan();
            expressions.add(Exp());
        }
        if (!expressions.isEmpty() && expressions.getLast() instanceof AdjustNumberOfValuesNode adjustment) {
            // in expression lists, we strip value pruning for the last value,
            // effectively flattening the multivalue into the expression-list
            assert adjustment.requested == 1 : "an expression should always prune itself to exactly 1 value";
            expressions.set(expressions.size() - 1, adjustment.multivalue);
        }
        return expressions.toArray(IRElement[]::new);
    }

    private static final EnumSet<TokenType> EXP_START = EnumSet.of(NIL, FALSE, TRUE, NUMERAL, LITERAL_STRING, TDOT,
            FUNCTION, LPAR, IDENT, NOT, HASH, SUB, BXOR, LBRAC);

    private IRElement Exp() {
        // or
        IRElement result = BinOp1();
        while (ltok == OR) {
            scan();
            Position pos = cur.pos();
            IRElement y = BinOp1();
            result = new OrNode(pos, result, y);
        }
        return result;
    }

    private IRElement BinOp1() {
        // and
        IRElement result = BinOp2();
        while (ltok == AND) {
            scan();
            Position pos = cur.pos();
            IRElement y = BinOp2();
            result = new AndNode(pos, result, y);
        }
        return result;
    }

    private IRElement BinOp2() {
        // < > <= >= ~= ==
        IRElement result = BinOp3();
        loop:
        for (; ; ) {
            Position opPos = la.pos();
            switch (ltok) {
                case LT, GT, LE, GE -> {
                    var op = ltok;
                    scan();
                    IRElement y = BinOp3();
                    result = switch (op) {
                        case GT -> new BinaryOperationNode(opPos, LT, y, result);
                        case GE -> new BinaryOperationNode(opPos, LE, y, result);
                        default -> new BinaryOperationNode(opPos, op, result, y);
                    };
                }
                case EQ, NE -> {
                    var op = ltok;
                    scan();
                    IRElement y = BinOp3();
                    result = new BinaryOperationNode(opPos, EQ, result, y);
                    if (op == NE) {
                        result = new NotNode(opPos, result);
                    }
                }
                default -> {
                    break loop;
                }
            }
        }
        return result;
    }

    private IRElement BinOp3() {
        // |
        IRElement result = BinOp4();
        while (ltok == BOR) {
            scan();
            Position pos = cur.pos();
            IRElement y = BinOp4();
            result = new BinaryOperationNode(pos, BOR, result, y);
        }
        return result;
    }

    private IRElement BinOp4() {
        // ~
        IRElement result = BinOp5();
        while (ltok == BXOR) {
            scan();
            Position pos = cur.pos();
            IRElement y = BinOp5();
            result = new BinaryOperationNode(pos, BXOR, result, y);
        }
        return result;
    }

    private IRElement BinOp5() {
        // &
        IRElement result = BinOp6();
        while (ltok == BAND) {
            scan();
            Position pos = cur.pos();
            IRElement y = BinOp6();
            result = new BinaryOperationNode(pos, BAND, result, y);
        }
        return result;
    }

    private IRElement BinOp6() {
        // << >>
        IRElement result = BinOp7();
        while (ltok == SHL || ltok == SHR) {
            var op = ltok;
            scan();
            Position pos = cur.pos();
            IRElement y = BinOp7();
            result = new BinaryOperationNode(pos, op, result, y);
        }
        return result;
    }

    private IRElement BinOp7() {
        // ..
        IRElement result = BinOp8();
        while (ltok == DDOT) {
            scan();
            Position pos = cur.pos();
            IRElement y = BinOp8();
            result = new BinaryOperationNode(pos, DDOT, result, y);
        }
        return result;
    }

    private IRElement BinOp8() {
        // + -
        IRElement result = BinOp9();
        for (; ; ) {
            if (ltok == ADD || ltok == SUB) {
                var op = ltok;
                scan();
                Position pos = cur.pos();
                IRElement y = BinOp9();
                result = new BinaryOperationNode(pos, op, result, y);
            } else {
                break;
            }
        }
        return result;
    }

    private IRElement BinOp9() {
        // * / // %
        IRElement result = UnOp();
        loop:
        for (; ; ) {
            switch (ltok) {
                case MULT, DIV, FDIV, MOD -> {
                    var op = ltok;
                    scan();
                    Position pos = cur.pos();
                    IRElement y = UnOp();
                    result = new BinaryOperationNode(pos, op, result, y);
                }
                default -> {
                    break loop;
                }
            }
        }
        return result;
    }

    private IRElement UnOp() {
        // not # - ~
        var opList = new Stack<Tuple<Position, TokenType>>();
        loop:
        for (; ; ) {
            switch (ltok) {
                case NOT, HASH, SUB, BXOR -> {
                    opList.push(new Tuple<>(la.pos(), ltok));
                    scan();
                }
                default -> {
                    break loop;
                }
            }
        }
        IRElement result = BinOp10();
        while (!opList.isEmpty()) {
            Tuple<Position, TokenType> op = opList.pop();
            result = switch (op.y()) {
                case NOT -> new NotNode(op.x(), result);
                case HASH, SUB, BXOR -> new UnaryOperationNode(op.x(), op.y(), result);
                default -> throw new InternalLuaParsingError("should not reach");
            };
        }
        return result;
    }

    private IRElement BinOp10() {
        // ^
        IRElement result = TermExp();
        while (ltok == EXPONENT) {
            scan();
            Position pos = cur.pos();
            IRElement y = TermExp();
            result = new BinaryOperationNode(pos, EXPONENT, result, y);
        }
        return result;
    }

    private IRElement TermExp() {
        // constants, funcdef or ValExp
        switch (ltok) {
            case TDOT -> {
                scan();
                LocalVariableAccessor paramsArg = symTab.findLocal("...");
                if (paramsArg == null) {
                    throw new LuaParserException(cur.pos(), "cannot use '...' outside a vararg function");
                }
                return new AdjustNumberOfValuesNode(1, new LocalAccessNode(cur.pos(), paramsArg));
            }
            case FUNCTION -> {
                scan();
                return FuncBody(false);
            }
            default -> {
                return OopValExp();
            }
        }
    }

    private static final EnumSet<TokenType> OOP_CALLABLE_LITERAL_START = EnumSet.of(NIL, TRUE, FALSE, NUMERAL, LITERAL_STRING, LBRAC);

    private IRElement OopCallableLiteral() {
        switch (ltok) {
            case NIL, TRUE, FALSE, NUMERAL, LITERAL_STRING -> {
                scan();
                return ConstantNode.of(cur);
            }
            case LBRAC -> {
                return TableConstructor();
            }
            default -> {
                throw new InternalLuaParsingError("should not reach");
            }
        }
    }

    private IRElement FuncBody(boolean hasSelf) {
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
        Position _pos = cur.pos();
        boolean hasParamsArg = !ps.isEmpty() && ps.get(ps.size() - 1).type() == TDOT;
        symTab.openFunctionScope(hasParamsArg, ps.stream().filter(tk -> tk.type() != TDOT).map(Token::stVal).toArray(String[]::new));
        var outerCb = cb;
        cb = new CFGBuilder(hasParamsArg);
        Block();
        var closed = symTab.closeFunctionScope(cb);
        check(END);
        ControlFlowGraph cfg = cb.finish(cur.pos());
        cb = outerCb;
        return new FunctionDefNode(_pos, false, closed.captures(), closed.arguments(), cfg);
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

    private IRElement[] Args() {
        IRElement[] result;
        if (ltok == LPAR) {
            scan();
            if (EXP_START.contains(ltok)) {
                result = ExpList();
            } else {
                result = new IRElement[0];
            }
            check(RPAR);
        } else if (ltok == LITERAL_STRING) {
            scan();
            result = new IRElement[]{ConstantNode.of(cur)};
        } else {
            result = new IRElement[]{TableConstructor()};
        }
        return result;
    }

    private IRElement TableConstructor() {
        check(LBRAC);
        Position tpos = cur.pos();
        ArrayList<Tuple<IRElement, IRElement>> keyVals;
        if (ltok == RBRAC) {
            scan();
            keyVals = new ArrayList<>();
        } else {
            keyVals = FieldList();
        }
        // reorder so that the keyless args are last and have ascending integer indices
        var keylessList = new ArrayList<Tuple<IRElement, IRElement>>();
        for (int i = 0; i < keyVals.size(); ) {
            var field = keyVals.get(i);
            if (field.x() == null) {
                keyVals.remove(i);
                // keyless field with ascending integer key
                keylessList.add(new Tuple<>(ConstantNode.ofInteger(field.y().pos, keylessList.size() + 1), field.y()));
                // incremented by removing element
            } else {
                // normal increment
                i++;
            }
        }
        keyVals.addAll(keylessList);
        return new TableConstructionNode(tpos, keyVals.toArray(Tuple[]::new));
    }

    private ArrayList<Tuple<IRElement, IRElement>> FieldList() {
        var fieldList = new ArrayList<Tuple<IRElement, IRElement>>();
        fieldList.add(Field());
        while ((ltok == COMMA || ltok == SEMICOLON) && lla.type() != RBRAC) {
            FieldSep();
            fieldList.add(Field());
        }
        if (ltok == COMMA || ltok == SEMICOLON) {
            FieldSep();
        }
        check(RBRAC);
        return fieldList;
    }

    private Tuple<IRElement, IRElement> Field() {
        IRElement key, value;
        if (ltok == LBRAK) {
            scan();
            key = Exp();
            check(RBRAK);
            check(ASSIGN);
            value = Exp();
        } else if (ltok == IDENT && lla.type() == ASSIGN) {
            scan();
            key = ConstantNode.of(cur);
            scan();  // ASSIGN
            value = Exp();
        } else {
            key = null;
            value = Exp();
        }
        return new Tuple<>(key, value);
    }

    private void FieldSep() {
        if (ltok == COMMA || ltok == SEMICOLON) {
            scan();
        }
    }
}