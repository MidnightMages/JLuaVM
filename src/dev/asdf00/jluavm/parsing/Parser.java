package dev.asdf00.jluavm.parsing;

import dev.asdf00.jluavm.parsing.container.Token;
import dev.asdf00.jluavm.parsing.container.TokenType;
import dev.asdf00.jluavm.parsing.container.VarInfo;
import dev.asdf00.jluavm.parsing.exceptions.LuaParserException;
import dev.asdf00.jluavm.parsing.exceptions.LuaLoadingException;
import dev.asdf00.jluavm.parsing.exceptions.LuaSemanticException;
import dev.asdf00.jluavm.utils.Tuple;

import java.util.ArrayList;
import java.util.EnumSet;

import static dev.asdf00.jluavm.parsing.container.TokenType.*;

public class Parser {
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
        if (!symTab.add(ident.stVal(), (attributes & 1) == 1, (attributes & 2) == 2)) {
            throw new LuaSemanticException(ident.pos(), "'%s' is defined twice!".formatted(cur.stVal()));
        }
    }

    public void parse() throws LuaLoadingException {
        cur = null;
        la = lexer.next();
        lla = lexer.next();
        ltok = la.type();
        Chunk();
    }

    private void Chunk() {
        Block(null);
        check(EOF);
    }

    private void Block(ArrayList<String> params) {
        symTab.enterScope(params);
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
        symTab.exitScope();
    }

    private static final EnumSet<TokenType> STAT_START = EnumSet.of(IDENT, LPAR, DCOLON, BREAK, GOTO, DO, WHILE, REPEAT, IF, FOR, FUNCTION, LOCAL);
    private void Stat() {
        switch (ltok) {
            case SEMICOLON -> {
                // nop
                scan();
            }
            case BREAK -> {
                scan();
            }
            case GOTO -> {
                scan();
                check(IDENT);
            }
            case DCOLON -> {
                // label
                scan();
                check(IDENT);
                check(DCOLON);
            }
            case DO -> {
                scan();
                Block(null);
                check(END);
            }
            case WHILE -> {
                scan();
                Exp();
                check(DO);
                Block(null);
                check(END);
            }
            case REPEAT -> {
                scan();
                Block(null);
                check(UNTIL);
                Exp();
            }
            case IF -> {
                scan();
                Exp();
                check(THEN);
                Block(null);
                while (ltok == ELSEIF) {
                    scan();
                    Exp();
                    check(THEN);
                    Block(null);
                }
                if (ltok == ELSE) {
                    scan();
                    Block(null);
                }
                check(END);
            }
            case FOR -> {
                scan();
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
                Block(null);
                check(END);
            }
            case FUNCTION -> {
                scan();
                check(IDENT);
                while (ltok == DOT) {
                    scan();
                    check(IDENT);
                }
                if (ltok == COLON) {
                    scan();
                    check(IDENT);
                }
                FuncBody();
            }
            case LOCAL -> {
                scan();
                if (ltok == FUNCTION) {
                    scan();
                    check(IDENT);
                    define(cur, 0);
                    FuncBody();
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
    private void StatExp() {
        VarInfo info;
        boolean onlyIdent;
        if (ltok == LPAR) {
            scan();
            Exp();
            check(RPAR);
            info = null;
            onlyIdent = false;
            if (ltok == LBRAK || ltok == DOT) {
                DeRef();
            } else {
                FuncCall();
            }
        } else {
            check(IDENT);
            info = symTab.get(cur.stVal());
            onlyIdent = true;
        }
        loop: for (;;) {
            switch (ltok) {
                case LBRAK, DOT -> {
                    DeRef();
                }
                case COLON, LPAR, LITERAL_STRING, LBRAC -> {
                    FuncCall();
                }
                default -> {
                    break loop;
                }
            }
            onlyIdent = false;
        }
        if (isAssignable()) {
            var qLocals = new ArrayList<Tuple<VarInfo, Boolean>>();
            qLocals.add(new Tuple<>(info, onlyIdent));
            while (ltok == COMMA) {
                scan();
                var packedInfo = ValExp();
                qLocals.add(packedInfo);
                if (!isAssignable()) {
                    throw new LuaParserException(la.pos(), "(At %s) Expected <%s>, got <%s>".formatted(la.pos(), DOT.rep, ltok.rep));
                }
            }
            check(ASSIGN);
            qLocals.forEach(p -> {
                if (p.x() != null && p.y()) {
                    p.x().setWritten();
                }
            });
            ExpList();
        }
    }

    private Tuple<VarInfo, Boolean> ValExp() {
        VarInfo info;
        boolean onlyIdent;
        if (ltok == LPAR) {
            scan();
            Exp();
            check(RPAR);
            info = null;
            onlyIdent = false;
        } else {
            check(IDENT);
            info = symTab.get(cur.stVal());
            onlyIdent = true;
        }
        loop: for (;;) {
            switch (ltok) {
                case LBRAK, DOT -> {
                    DeRef();
                }
                case COLON, LPAR, LITERAL_STRING, LBRAC -> {
                    FuncCall();
                }
                default -> {
                    break loop;
                }
            }
            onlyIdent = false;
        }
        return new Tuple<>(info, onlyIdent);
    }

    private void DeRef() {
        if (ltok == LBRAK) {
            scan();
            Exp();
            check(RBRAK);
        } else {
            check(DOT);
            check(IDENT);
        }
    }

    private void FuncCall() {
        if (ltok == COLON) {
            scan();
            check(IDENT);
        }
        Args();
    }

    private void ExpList() {
        Exp();
        while (ltok == COMMA) {
            scan();
            Exp();
        }
    }

    private static final EnumSet<TokenType> EXP_START = EnumSet.of(NIL, FALSE, TRUE, NUMERAL, LITERAL_STRING, TDOT,
            FUNCTION, LPAR, IDENT, NOT, HASH, SUB, BXOR, LBRAC);
    private void Exp() {
        // or
        BinOp1();
        while (ltok == OR) {
            scan();
            BinOp1();
        }
    }

    private void BinOp1() {
        // and
        BinOp2();
        while (ltok == AND) {
            scan();
            BinOp2();
        }
    }

    private void BinOp2() {
        // < > <= >= ~= ==
        BinOp3();
        loop: for (;;) {
            switch (ltok) {
                case LT -> {
                    scan();
                }
                case GT -> {
                    scan();
                }
                case LE -> {
                    scan();
                }
                case GE -> {
                    scan();
                }
                case NE -> {
                    scan();
                }
                case EQ -> {
                    scan();
                }
                default -> {
                    break loop;
                }
            }
            BinOp3();
        }
    }

    private void BinOp3() {
        // |
        BinOp4();
        while (ltok == BOR) {
            scan();
            BinOp4();
        }
    }

    private void BinOp4() {
        // ~
        BinOp5();
        while (ltok == BXOR) {
            scan();
            BinOp5();
        }
    }

    private void BinOp5() {
        // &
        BinOp6();
        while (ltok == BAND) {
            scan();
            BinOp6();
        }
    }

    private void BinOp6() {
        // << >>
        BinOp7();
        for (;;) {
            if (ltok == SHL) {
                scan();
            } else if (ltok == SHR) {
                scan();
            } else {
                break;
            }
            BinOp7();
        }
    }

    private void BinOp7() {
        // ..
        BinOp8();
        while (ltok == DDOT) {
            scan();
            BinOp8();
        }
    }

    private void BinOp8() {
        // + -
        BinOp9();
        for (;;) {
            if (ltok == ADD) {
                scan();
            } else if (ltok == SUB) {
                scan();
            } else {
                break;
            }
            BinOp9();
        }
    }

    private void BinOp9() {
        // * / // %
        UnOp();
        loop: for (;;) {
            switch (ltok) {
                case MULT -> {
                    scan();
                }
                case DIV -> {
                    scan();
                }
                case FDIV -> {
                    scan();
                }
                case MOD -> {
                    scan();
                }
                default -> {
                    break loop;
                }
            }
            UnOp();
        }
    }

    private void UnOp() {
        // not # - ~
        loop: for (;;) {
            switch (ltok) {
                case NOT -> {
                    scan();
                }
                case HASH -> {
                    scan();
                }
                case SUB -> {
                    scan();
                }
                case BXOR -> {
                    scan();
                }
                default -> {
                    break loop;
                }
            }
        }
        BinOp10();
    }

    private void BinOp10() {
        // ^
        TermExp();
        while (ltok == EXPONENT) {
            scan();
            TermExp();
        }
    }

    private void TermExp() {
        // constants, funcdef or ValExp
        switch (ltok) {
            case NIL -> {
                scan();
            }
            case FALSE -> {
                scan();
            }
            case TRUE -> {
                scan();
            }
            case NUMERAL -> {
                scan();
            }
            case LITERAL_STRING -> {
                scan();
            }
            case TDOT -> {
                scan();
            }
            case FUNCTION -> {
                scan();
                FuncBody();
            }
            case LBRAC -> {
                TableConstructor();
            }
            default -> {
                ValExp();
            }
        }
    }

    private void FuncBody() {
        check(LPAR);
        ArrayList<String> pars;
        if (ltok == TDOT || ltok == IDENT) {
            pars = ParList();
        } else {
            pars = new ArrayList<>();
        }
        check(RPAR);
        Block(pars);
        check(END);
    }

    private ArrayList<String> ParList() {
        ArrayList<String> pars = new ArrayList<>();
        if (ltok == TDOT) {
            scan();
        } else {
            check(IDENT);
            pars.add(cur.stVal());
            while (ltok == COMMA && lla.type() != TDOT) {
                scan();
                check(IDENT);
                pars.add(cur.stVal());
            }
            if (ltok == COMMA) {
                scan();
                check(TDOT);
            }
        }
        return pars;
    }

    private static final EnumSet<TokenType> ARGS_START = EnumSet.of(LPAR, LITERAL_STRING, LBRAC);
    private void Args() {
        if (ltok == LPAR) {
            scan();
            if (EXP_START.contains(ltok)) {
                ExpList();
            }
            check(RPAR);
        } else if (ltok == LITERAL_STRING) {
            scan();
        } else {
            TableConstructor();
        }
    }

    private void TableConstructor() {
        check(LBRAC);
        if (ltok == RBRAC) {
            scan();
        } else {
            FieldList();
        }
    }

    private void FieldList() {
        Field();
        while ((ltok == COMMA || ltok == SEMICOLON) && lla.type() != RBRAC) {
            FieldSep();
            Field();
        }
        if (ltok == COMMA || ltok == SEMICOLON) {
            FieldSep();
        }
        check(RBRAC);
    }

    private void Field() {
        if (ltok == LBRAK) {
            scan();
            Exp();
            check(RBRAK);
            check(ASSIGN);
            Exp();
        } else if (ltok == IDENT && lla.type() == ASSIGN) {
            scan();
            scan();  // ASSIGN
            Exp();
        } else {
            Exp();
        }
    }

    private void FieldSep() {
        if (ltok == COMMA || ltok == SEMICOLON) {
            scan();
        }
    }
}
