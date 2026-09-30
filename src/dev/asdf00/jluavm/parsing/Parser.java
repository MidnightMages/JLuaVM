package dev.asdf00.jluavm.parsing;

import dev.asdf00.jluavm.exceptions.loading.LuaParserException;
import dev.asdf00.jluavm.parsing.LocalVariable.LocalVariableAccessor;
import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.container.Token;
import dev.asdf00.jluavm.parsing.container.TokenType;
import dev.asdf00.jluavm.parsing.ir.ast.AST;
import dev.asdf00.jluavm.parsing.ir.ast.AST.*;
import dev.asdf00.jluavm.runtime.utils.Singletons;
import dev.asdf00.jluavm.utils.Triple;
import dev.asdf00.jluavm.utils.Tuple;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Stack;

import static dev.asdf00.jluavm.parsing.Lexer.STARTING_POS;
import static dev.asdf00.jluavm.parsing.container.TokenType.*;

public final class Parser {
    private final SymbolTable symTab;
    private final Lexer lexer;
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

    private AST genAccess(Position pos, LocalVariableAccessor accessor, String ident) {
        if (accessor != null) {
            return new LocalAccessNode(pos, accessor);
        }
        if ("_ENV".equals(ident)) {
            return new EnvAccessNode(pos);
        }
        return new IndexedAccessNode(pos, getEnv(pos), ConstantNode.ofIdent(pos, ident));
    }

    private AST getEnv(Position pos) {
        if (symTab.isEnvRedeclared()) {
            return new LocalAccessNode(pos, symTab.findLocal("_ENV"));
        } else {
            return new EnvAccessNode(pos);
        }
    }

    /*-
     * =================================================================================================================
     *    PARSING   PARSING   PARSING   PARSING   PARSING   PARSING   PARSING   PARSING   PARSING   PARSING   PARSING
     * =================================================================================================================
     */

    public AST parse() throws LuaParserException {
        cur = null;
        la = lexer.next();
        lla = lexer.next();
        ltok = la.type();
        return Chunk();
    }

    private AST Chunk() {
        symTab.openFunctionScope(true);
        var block = Block();
        var arguments = new ArrayList<LocalVariableAccessor>();
        symTab.closeFunctionScope(arguments, null);
        check(EOF);
        return new FunctionDefNode(new Position(0, 0, 0), true,
                Singletons.EMPTY_LOCAL_ACCESSOR_ARRAY, true, arguments.toArray(LocalVariableAccessor[]::new), block);
    }

    private AST Block() {
        Position _pos = cur == null ? STARTING_POS : cur.pos();
        var statements = new ArrayList<AST>();
        while (STAT_START.contains(ltok)) {
            AST s = Stat();
            if (s != null) {
                if (s instanceof BlockNode b && !b.isLexicalBlock()) {
                    Collections.addAll(statements, b.statements());
                } else {
                    statements.add(s);
                }
            }
        }
        if (ltok == RETURN) {
            scan();
            Position pos = cur.pos();
            AST[] returnValues;
            if (EXP_START.contains(ltok)) {
                returnValues = ExpList();
            } else {
                returnValues = new AST[0];
            }
            if (ltok == SEMICOLON) {
                scan();
            }
            // TODO check for tailcalls on the last return value
            statements.add(new ReturnNode(pos, returnValues));
        }
        if (statements.size() == 1 /* TODO && !hasClosableVars */) {
            return statements.get(0);
        } else {
            // TODO add closing statements
            return new BlockNode(_pos, true, statements.toArray(AST[]::new));
        }
    }

    private static final EnumSet<TokenType> STAT_START = EnumSet.of(SEMICOLON, IDENT, LPAR, DCOLON, BREAK, GOTO, DO, WHILE, REPEAT, IF, FOR, FUNCTION, LOCAL,
            NIL, FALSE, TRUE, NUMERAL, LITERAL_STRING, LBRAC);

    private AST Stat() {
        AST statement;
        Position _pos = la.pos();
        switch (ltok) {
            case SEMICOLON -> {
                // nop
                scan();
                statement = new NopNode(_pos);
            }
            case BREAK -> {
                scan();
                symTab.labelNotLast();
                if (!symTab.isInsideLoop()) {
                    throw new LuaParserException(_pos, "'break' is not inside a loop");
                }
                statement = new GotoNode(_pos, symTab.resolveBreak());
            }
            case GOTO -> {
                scan();
                symTab.labelNotLast();
                check(IDENT);
                statement = new GotoNode(_pos, symTab.resolveJump(cur.stVal()));
            }
            case DCOLON -> {
                // label
                scan();
                check(IDENT);
                statement = new LabelNode(_pos, symTab.declareJumpLabel(cur.stVal()));
                check(DCOLON);
            }
            case DO -> {
                scan();
                symTab.labelNotLast();
                symTab.openBlockScope();
                statement = Block();
                symTab.closeScope();
                check(END);
            }
            case WHILE -> {
                scan();
                symTab.labelNotLast();
                AST entryCond = Exp();
                check(DO);
                symTab.openLoopScope();
                AST body = Block();
                var breakLabelBox = new AST.JumpDestinationNode[1];
                symTab.closeLoopScope(breakLabelBox); // close loop scope
                check(END);
                statement = new WhileNode(_pos, entryCond, body, breakLabelBox[0]);
            }
            case REPEAT -> {
                scan();
                symTab.labelNotLast();
                symTab.openLoopScope();
                AST body = Block();
                check(UNTIL);
                Position exitPos = cur.pos();
                AST exitCond = Exp();
                var breakLabelBox = new AST.JumpDestinationNode[1];
                symTab.closeLoopScope(breakLabelBox); // close loop scope
                statement = new WhileNode(_pos,
                        ConstantNode.ofBoolean(_pos, true),
                        BlockNode.append(body, new IfNode(exitPos, exitCond, new GotoNode(exitPos, breakLabelBox[0]))),
                        breakLabelBox[0]);
            }
            case IF -> {
                scan();
                symTab.labelNotLast();
                AST condition = Exp();
                check(THEN);
                symTab.openBlockScope();
                Position thenPos = cur.pos();
                AST thenBlock = Block();
                symTab.closeScope();
                var elifs = new Stack<Triple<Position, AST, AST>>();
                while (ltok == ELSEIF) {
                    scan();
                    Position elifPos = cur.pos();
                    AST elifCond = Exp();
                    check(THEN);
                    symTab.openBlockScope();
                    AST elifBlock = Block();
                    symTab.closeScope();
                    elifs.push(new Triple<>(elifPos, elifCond, elifBlock));
                }
                AST elseBlock = null;
                if (ltok == ELSE) {
                    scan();
                    symTab.openBlockScope();
                    elseBlock = Block();
                    symTab.closeScope();
                }
                check(END);
                // nest else-ifs properly
                AST nestedElse = elseBlock;
                while (!elifs.isEmpty()) {
                    var elif = elifs.pop();
                    nestedElse = new IfNode(elif.x(), elif.y(), elif.z(), nestedElse);
                }
                // overwrite original elseBlock
                elseBlock = nestedElse;
                // create node
                statement = new IfNode(_pos, condition, thenBlock, elseBlock);
            }
            case FOR -> {
                scan();
                symTab.labelNotLast();
                check(IDENT);
                Token ctrlToken = cur;
                if (ltok == ASSIGN) {
                    scan();
                    // numerical for
                    symTab.openBlockScope();
                    LocalVariableAccessor _internalControl = symTab.declareLocal("$internalControl$");
                    // start
                    AST initialValue = Exp();
                    check(COMMA);
                    // end
                    AST upperBound = Exp();
                    Position ubPos = la.pos();
                    LocalVariableAccessor _upperBound = symTab.declareLocal("$upperBound$");
                    AST step;
                    LocalVariableAccessor _step = symTab.declareLocal("$step$");
                    if (ltok == COMMA) {
                        scan();
                        // step
                        step = Exp();
                    } else {
                        // default step width is 1
                        step = ConstantNode.ofInteger(ubPos, 1);
                    }
                    symTab.openLoopScope();
                    LocalVariableAccessor actualCtrlVar = symTab.declareLocal(ctrlToken.stVal(), 0);
                    check(DO);
                    Position bpos = cur.pos();
                    AST body = Block();
                    var breakLabelBox = new AST.JumpDestinationNode[1];
                    symTab.closeLoopScope(breakLabelBox); // close loop scope
                    symTab.closeScope(); // close scope of internal variables
                    check(END);

                    /*-
                    We use Lua 5.4 semantics here. This will be constructed as:
                        do
                          local _internalControl = tonumber(initialValue)
                          local _upperBound = tonumber(upperBound)
                          local _step = tonumber(step)
                          if _step == 0 then
                            error("'for' step is zero")
                          end
                          while 0 < _step and _internalControl <= _upperBound or _step < 0 and _upperBound <= _internalControl do
                            local ctrlVar = _internalControl
                            do -- new scope to allow jumping to the bottom of the loop (homebrew 'continue')
                              -- BODY
                            end
                            try _internalControl = stepFor(_internalControl, _step) catch break end
                          end
                        end
                    'stepFor' may throw a LuaOverflowException, that is immediately caught, which causes a break.
                     */

                    statement = new BlockNode(_pos, true,
                            new LocalDeclarationNode(_pos, _internalControl),
                            new LocalDeclarationNode(_pos, _upperBound),
                            new LocalDeclarationNode(_pos, _step),
                            new AssignmentNode(_pos,
                                    new LocalAccessNode(_pos, _internalControl),
                                    new ToNumberOrFailNode(_pos, initialValue, "Bad 'for' initial value (number expected, got %s)")),
                            new AssignmentNode(_pos,
                                    new LocalAccessNode(_pos, _upperBound),
                                    new ToNumberOrFailNode(_pos, upperBound, "Bad 'for' limit (number expected, got %s)")),
                            new AssignmentNode(_pos,
                                    new LocalAccessNode(_pos, _step),
                                    new ToNumberOrFailNode(_pos, step, "Bad 'for' step (number expected, got %s)")),
                            new IfNode(_pos,
                                    new BinaryOperationNode(_pos, EQ,
                                            new LocalAccessNode(_pos, _step),
                                            ConstantNode.ofInteger(_pos, 0)),
                                    new ErrorNode(_pos, ConstantNode.ofString(_pos, "'for' step is zero"))),
                            new WhileNode(_pos,
                                    new OrNode(_pos,
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
                                                            new LocalAccessNode(_pos, _internalControl)))),
                                    new BlockNode(_pos, true,
                                            new LocalDeclarationNode(ctrlToken.pos(), actualCtrlVar),
                                            new AssignmentNode(ctrlToken.pos(), new LocalAccessNode(ctrlToken.pos(), actualCtrlVar),
                                                    new LocalAccessNode(ctrlToken.pos(), _internalControl)),
                                            new BlockNode(bpos, true, body),
                                            new ForStepNode(cur.pos(), _internalControl, _step, breakLabelBox[0])),
                                    breakLabelBox[0]));
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
                    LocalVariableAccessor closing = symTab.declareLocal("$closingVariable$", 3);
                    AST[] initials = ExpList();
                    check(DO);
                    final Position innerSetupPos = cur.pos();
                    symTab.openLoopScope();
                    // the first one of these is the control variable accessible to lua
                    LocalVariableAccessor[] loopVars = loopVarTokens.stream().map(t -> symTab.declareLocal(t.stVal())).toArray(LocalVariableAccessor[]::new);
                    AST body = Block();
                    var breakLabelBox = new AST.JumpDestinationNode[1];
                    symTab.closeLoopScope(breakLabelBox); // close loop scope
                    check(END);
                    symTab.closeScope(); // hidden vars scope

                    /*-
                    This gets expanded to:
                        do
                          local itrFunc, state, internalControlVar, closing<close> = initials  -- as a multi assignment
                          while true do
                            local internalControlVar, var2, ..., varN = itrFunc(state, internalControlVar)
                            if internalControlVar == nil then
                              break
                            end
                            local var1 = internalControlVar
                            -- BODY
                          end
                        end
                     */

                    var inLoopDeclarations = new LocalDeclarationNode[loopVars.length];
                    var inLoopSetupAccesses = new LocalAccessNode[loopVars.length];
                    for (int i = 0; i < inLoopSetupAccesses.length; i++) {
                        inLoopDeclarations[i] = new LocalDeclarationNode(innerSetupPos, loopVars[i]);
                        inLoopSetupAccesses[i] = new LocalAccessNode(innerSetupPos, loopVars[i]);
                    }
                    LocalAccessNode externalControl = inLoopSetupAccesses[0];
                    inLoopSetupAccesses[0] = new LocalAccessNode(innerSetupPos, internalControlVar);

                    statement = new BlockNode(_pos, true,
                            new LocalDeclarationNode(_pos, itrFunc),
                            new LocalDeclarationNode(_pos, state),
                            new LocalDeclarationNode(_pos, internalControlVar),
                            new LocalDeclarationNode(_pos, closing),
                            new AssignmentNode(_pos,
                                    new AST[]{
                                            new LocalAccessNode(_pos, itrFunc),
                                            new LocalAccessNode(_pos, state),
                                            new LocalAccessNode(_pos, internalControlVar),
                                            new LocalAccessNode(_pos, closing)
                                    },
                                    initials),
                            new WhileNode(_pos,
                                    ConstantNode.ofBoolean(_pos, true),
                                    new BlockNode(innerSetupPos, true,
                                            // TODO missing declarations
                                            // here
                                            new BlockNode(innerSetupPos, false,
                                                    inLoopDeclarations),
                                            new AssignmentNode(innerSetupPos,
                                                    inLoopSetupAccesses,
                                                    new AST[]{new FunctionCallNode(innerSetupPos,
                                                            new LocalAccessNode(innerSetupPos, itrFunc),
                                                            new LocalAccessNode(innerSetupPos, state),
                                                            new LocalAccessNode(innerSetupPos, internalControlVar))}),
                                            new IfNode(innerSetupPos,
                                                    new BinaryOperationNode(innerSetupPos, EQ,
                                                            inLoopSetupAccesses[0],
                                                            ConstantNode.ofNil(innerSetupPos)),
                                                    new GotoNode(innerSetupPos, breakLabelBox[0])),
                                            new AssignmentNode(innerSetupPos,
                                                    externalControl,
                                                    inLoopSetupAccesses[0]),
                                            body),
                                    breakLabelBox[0]));
                }
            }
            case FUNCTION -> {
                scan();
                symTab.labelNotLast();
                check(IDENT);
                Position apos = cur.pos();
                AST access = genAccess(cur.pos(), symTab.findLocal(cur.stVal()), cur.stVal());
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
                AST func = FuncBody(hasSelf);
                statement = new AssignmentNode(apos, access, func);
            }
            case LOCAL -> {
                scan();
                symTab.labelNotLast();
                if (ltok == FUNCTION) {
                    scan();
                    check(IDENT);
                    Position namePos = cur.pos();
                    LocalVariableAccessor funcVar = symTab.declareLocal(cur.stVal());
                    AST function = FuncBody(false);
                    // insert a fake write to correctly apply upvalue capturing semantics
                    funcVar.getVariable().setWritten(namePos);
                    statement = new BlockNode(_pos, false,
                            new LocalDeclarationNode(namePos, funcVar),
                            new AssignmentNode(namePos, new LocalAccessNode(namePos, funcVar), function));
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
                    AST[] expressions;
                    Position apos;
                    if (ltok == ASSIGN) {
                        scan();
                        apos = cur.pos();
                        expressions = ExpList();
                    } else {
                        // this is a pure declaration
                        apos = _pos;
                        expressions = new AST[0];
                    }
                    var localAccesses = new AST[localList.size()];
                    var operations = new AST[localList.size() + 1];
                    for (int i = 0; i < localAccesses.length; i++) {
                        Tuple<Token, Integer> localDefinition = localList.get(i);
                        Position lPos = localDefinition.x().pos();
                        LocalVariableAccessor local = symTab.declareLocal(localDefinition.x().stVal(), localDefinition.y());
                        // create declarations and pseudo accesses
                        operations[i] = new LocalDeclarationNode(lPos, local);
                        localAccesses[i] = new LocalAccessNode(lPos, local);
                    }
                    // add the assignment node
                    operations[operations.length - 1] = new AssignmentNode(apos, localAccesses, expressions);
                    statement = new BlockNode(_pos, false, operations);
                }
            }
            case IDENT, LPAR, NIL, FALSE, TRUE, NUMERAL, LITERAL_STRING, LBRAC -> {
                symTab.labelNotLast();
                statement = StatExp();
            }
            default -> throw new InternalLuaParsingError("unexpected statement start '%s'".formatted(ltok.name()));
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
                throw new LuaParserException(cur.pos(), "Only 'const' and 'close' are allowed as attributes, not '%s'!".formatted(cur.stVal()));
            }
            check(GT);
        }
        return rVal;
    }

    // private static final EnumSet<TokenType> DEREF_OR_FUNCCALL_START = EnumSet.of(LBRAK, DOT,
    // COLON, LPAR, LITERAL_STRING, LBRAC);

    private AST StatExp() {
        AST result;
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
            var assignTargets = new ArrayList<AST>();
            assignTargets.add(result);
            while (ltok == COMMA) {
                scan();
                AST assignAccess = OopValExp();
                assignTargets.add(assignAccess);
                if (!isAssignable()) {
                    throw new LuaParserException(la.pos(), "Expected <%s>, got <%s>".formatted(DOT.rep, ltok.rep));
                }
            }
            check(ASSIGN);
            Position apos = cur.pos();
            AST[] expressions = ExpList();
            result = new AssignmentNode(apos, assignTargets.toArray(AST[]::new), expressions);
        } else {
            if (result instanceof AdjustNumberOfValuesNode adjustNode && (adjustNode.multivalue() instanceof FunctionCallNode || adjustNode.multivalue() instanceof OopCallNode)) {
                // if a function call is a statement, we drop the return values instead of pruning
                // them
                result = new AdjustNumberOfValuesNode(0, adjustNode.multivalue());
            } else {
                throw new LuaParserException(cur.pos(), "(At %s) Expected <%s>, got <%s>".formatted(cur.pos(), EQ.rep, cur.type().rep));
            }
        }
        return result;
    }

    private AST OopValExp() {
        if (OOP_CALLABLE_LITERAL_START.contains(ltok)) {
            AST result = OopCallableLiteral();
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

    private AST ValExp() {
        AST result;
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

    private AST DeRef(AST target) {
        AST index;
        if (ltok == LBRAK) {
            scan();
            index = Exp();
            check(RBRAK);
        } else {
            check(DOT);
            check(IDENT);
            index = ConstantNode.of(cur);
        }
        return new IndexedAccessNode(target.pos(), target, index);
    }

    private AST FuncCall(AST callable) {
        AST object = null;
        AST func = callable;
        if (ltok == COLON) {
            scan();
            object = callable;
            check(IDENT);
            func = ConstantNode.of(cur);
        }
        Position pos = la.pos();
        AST[] args = Args();
        // _ENV is passed for type extension functions on OOP calls
        // per default, return values are adjusted to 1; this may be stripped later
        return new AdjustNumberOfValuesNode(1, object == null
                ? new FunctionCallNode(pos, func, args)
                : new OopCallNode(pos, getEnv(pos), object, func, args));
    }

    private AST OopFuncCall(AST object) {
        check(COLON);
        // code gen in FunctionCallNode relies on this being a constant ident
        check(IDENT);
        AST callable = ConstantNode.of(cur);
        Position pos = la.pos();
        AST[] args = Args();
        // _ENV is passed for type extension functions on OOP calls
        return new AdjustNumberOfValuesNode(pos, 1,
                new OopCallNode(pos, getEnv(pos), object, callable, args));
    }

    private AST[] ExpList() {
        var expressions = new ArrayList<AST>();
        expressions.add(Exp());
        while (ltok == COMMA) {
            scan();
            expressions.add(Exp());
        }
        if (!expressions.isEmpty() && expressions.getLast() instanceof AdjustNumberOfValuesNode adjustment) {
            // in expression lists, we strip value pruning for the last value,
            // effectively flattening the multivalue into the expression-list
            assert adjustment.requested() == 1 : "an expression should always prune itself to exactly 1 value";
            expressions.set(expressions.size() - 1, adjustment.multivalue());
        }
        return expressions.toArray(AST[]::new);
    }

    private static final EnumSet<TokenType> EXP_START = EnumSet.of(NIL, FALSE, TRUE, NUMERAL, LITERAL_STRING, TDOT,
            FUNCTION, LPAR, IDENT, NOT, HASH, SUB, BXOR, LBRAC);

    private AST Exp() {
        // or
        AST result = BinOp1();
        while (ltok == OR) {
            scan();
            Position pos = cur.pos();
            AST y = BinOp1();
            result = new OrNode(pos, result, y);
        }
        return result;
    }

    private AST BinOp1() {
        // and
        AST result = BinOp2();
        while (ltok == AND) {
            scan();
            Position pos = cur.pos();
            AST y = BinOp2();
            result = new AndNode(pos, result, y);
        }
        return result;
    }

    private AST BinOp2() {
        // < > <= >= ~= ==
        AST result = BinOp3();
        loop:
        for (; ; ) {
            Position opPos = la.pos();
            switch (ltok) {
                case LT, GT, LE, GE -> {
                    var op = ltok;
                    scan();
                    AST y = BinOp3();
                    result = switch (op) {
                        case GT -> new BinaryOperationNode(opPos, LT, y, result);
                        case GE -> new BinaryOperationNode(opPos, LE, y, result);
                        default -> new BinaryOperationNode(opPos, op, result, y);
                    };
                }
                case EQ, NE -> {
                    var op = ltok;
                    scan();
                    AST y = BinOp3();
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

    private AST BinOp3() {
        // |
        AST result = BinOp4();
        while (ltok == BOR) {
            scan();
            Position pos = cur.pos();
            AST y = BinOp4();
            result = new BinaryOperationNode(pos, BOR, result, y);
        }
        return result;
    }

    private AST BinOp4() {
        // ~
        AST result = BinOp5();
        while (ltok == BXOR) {
            scan();
            Position pos = cur.pos();
            AST y = BinOp5();
            result = new BinaryOperationNode(pos, BXOR, result, y);
        }
        return result;
    }

    private AST BinOp5() {
        // &
        AST result = BinOp6();
        while (ltok == BAND) {
            scan();
            Position pos = cur.pos();
            AST y = BinOp6();
            result = new BinaryOperationNode(pos, BAND, result, y);
        }
        return result;
    }

    private AST BinOp6() {
        // << >>
        AST result = BinOp7();
        while (ltok == SHL || ltok == SHR) {
            var op = ltok;
            scan();
            Position pos = cur.pos();
            AST y = BinOp7();
            result = new BinaryOperationNode(pos, op, result, y);
        }
        return result;
    }

    private AST BinOp7() {
        // ..
        AST result = BinOp8();
        while (ltok == DDOT) {
            scan();
            Position pos = cur.pos();
            AST y = BinOp8();
            result = new BinaryOperationNode(pos, DDOT, result, y);
        }
        return result;
    }

    private AST BinOp8() {
        // + -
        AST result = BinOp9();
        for (; ; ) {
            if (ltok == ADD || ltok == SUB) {
                var op = ltok;
                scan();
                Position pos = cur.pos();
                AST y = BinOp9();
                result = new BinaryOperationNode(pos, op, result, y);
            } else {
                break;
            }
        }
        return result;
    }

    private AST BinOp9() {
        // * / // %
        AST result = UnOp();
        loop:
        for (; ; ) {
            switch (ltok) {
                case MULT, DIV, FDIV, MOD -> {
                    var op = ltok;
                    scan();
                    Position pos = cur.pos();
                    AST y = UnOp();
                    result = new BinaryOperationNode(pos, op, result, y);
                }
                default -> {
                    break loop;
                }
            }
        }
        return result;
    }

    private AST UnOp() {
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
        AST result = BinOp10();
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

    private AST BinOp10() {
        // ^
        AST result = TermExp();
        while (ltok == EXPONENT) {
            scan();
            Position pos = cur.pos();
            AST y = TermExp();
            result = new BinaryOperationNode(pos, EXPONENT, result, y);
        }
        return result;
    }

    private AST TermExp() {
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

    private AST OopCallableLiteral() {
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

    private AST FuncBody(boolean hasSelf) {
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
        symTab.openFunctionScope(hasParamsArg, ps.stream().map(tk -> tk.type() == TDOT ? "..." : tk.stVal()).toArray(String[]::new));
        AST body = Block();
        var arguments = new ArrayList<LocalVariableAccessor>();
        var captures = new ArrayList<LocalVariableAccessor>();
        symTab.closeFunctionScope(arguments, captures);
        check(END);
        return new FunctionDefNode(_pos, false, captures.toArray(LocalVariableAccessor[]::new),
                hasParamsArg, arguments.toArray(LocalVariableAccessor[]::new), body);
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

    private AST[] Args() {
        AST[] result;
        if (ltok == LPAR) {
            scan();
            if (EXP_START.contains(ltok)) {
                result = ExpList();
            } else {
                result = new AST[0];
            }
            check(RPAR);
        } else if (ltok == LITERAL_STRING) {
            scan();
            result = new AST[]{ConstantNode.of(cur)};
        } else {
            result = new AST[]{TableConstructor()};
        }
        return result;
    }

    private AST TableConstructor() {
        check(LBRAC);
        Position tpos = cur.pos();
        ArrayList<Tuple<AST, AST>> keyVals;
        if (ltok == RBRAC) {
            scan();
            keyVals = new ArrayList<>();
        } else {
            keyVals = FieldList();
        }
        // reorder so that the keyless args are last and have ascending integer indices
        var keylessList = new ArrayList<Tuple<AST, AST>>();
        for (int i = 0; i < keyVals.size(); ) {
            var field = keyVals.get(i);
            if (field.x() == null) {
                keyVals.remove(i);
                // keyless field with ascending integer key
                keylessList.add(new Tuple<>(ConstantNode.ofInteger(field.y().pos(), keylessList.size() + 1), field.y()));
                // incremented by removing element
            } else {
                // normal increment
                i++;
            }
        }
        keyVals.addAll(keylessList);
        return new TableConstructionNode(tpos, keyVals.toArray(Tuple[]::new));
    }

    private ArrayList<Tuple<AST, AST>> FieldList() {
        var fieldList = new ArrayList<Tuple<AST, AST>>();
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

    private Tuple<AST, AST> Field() {
        AST key, value;
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