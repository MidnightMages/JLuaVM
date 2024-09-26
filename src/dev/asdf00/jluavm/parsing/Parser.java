package dev.asdf00.jluavm.parsing;

import dev.asdf00.jluavm.parsing.exceptions.LuaParserException;
import dev.asdf00.jluavm.parsing.exceptions.LuaReadingException;

import java.util.EnumSet;

import static dev.asdf00.jluavm.parsing.TokenType.*;

public class Parser {
    private final Lexer lexer;
    private TokenType ltok = EOF;  // TokenType of la
    private Token cur;  // current token
    private Token la;  // lookahead token
    private Token lla;  // lookahead token 2

    public Parser(String input) {
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

    public void parse() throws LuaReadingException {
        cur = lexer.next();
        la = lexer.next();
        lla = lexer.next();
        ltok = la.type();
        Chunk();
    }

    private void Chunk() {
        Block();
        check(EOF);
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
                Block();
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
                Block();
                check(UNTIL);
                Exp();
            }
            case IF -> {
                scan();
                Exp();
                check(THEN);
                Block();
                while (ltok == ELSEIF) {
                    scan();
                    Exp();
                    check(THEN);
                    Block();
                }
                if (ltok == ELSE) {
                    scan();
                    Block();
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
                Block();
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
                    FuncBody();
                } else {
                    check(IDENT);
                    Attrib();
                    while (ltok == COMMA) {
                        scan();
                        check(IDENT);
                        Attrib();
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

    private void Attrib() {
        if (ltok == LT) {
            scan();
            check(IDENT);
            check(GT);
        }
    }

    private void ValExp() {
        if (ltok == LPAR) {
            scan();
            Exp();
            check(RPAR);
        } else {
            check(IDENT);
        }
        for (;;) {
            if (ltok == LBRAK) {
                scan();
                Exp();
                check(RBRAK);
            } else if (ltok == DOT) {
                scan();
                check(IDENT);
            } else if (ltok == COLON || ARGS_START.contains(ltok)) {
                if (ltok == COLON) {
                    scan();
                    check(IDENT);
                }
                Args();
            } else {
                break;
            }
        }
    }

    private void StatExp() {
        if (ltok == LPAR) {
            scan();
            Exp();
            check(RPAR);
            if (STAT_EXP_2_START.contains(ltok)) {
                StatExp2();
            }
        } else {
            check(IDENT);
            if (ltok == COMMA || ltok == ASSIGN) {
                VarAssign();
            } else {
                if (STAT_EXP_2_START.contains(ltok)) {
                    StatExp2();
                }
            }
        }
    }

    private static final EnumSet<TokenType> STAT_EXP_2_START = EnumSet.of(LBRAK, COLON, DOT, LPAR);
    private void StatExp2() {
        if (ltok != DOT) {
            if (ltok == LBRAK) {
                scan();
                Exp();
                check(RBRAK);
            } else {
                if (ltok == COLON) {
                    scan();
                    check(IDENT);
                }
                Args();
            }
            if (STAT_EXP_2_START.contains(ltok)) {
                StatExp2();
            }
        } else {
            check(DOT);
            check(IDENT);
            if (ltok == COMMA || ltok == ASSIGN) {
                VarAssign();
            } else {
                if (STAT_EXP_2_START.contains(ltok)) {
                    StatExp2();
                }
            }
        }
    }

    private void VarAssign() {
        while (ltok == COMMA) {
            scan();
            StatExp();
        }
        check(ASSIGN);
        ExpList();
    }

    private void ExpList() {
        Exp();
        while (ltok == COMMA) {
            scan();
            Exp();
        }
    }

    private static final EnumSet<TokenType> EXP_START = EnumSet.of(NIL, FALSE, TRUE, NUMERAL, LITERAL_STRING, TDOT,
            FUNCTION, LPAR, IDENT, NOT, HASH, SUB, BXOR);
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
        if (ltok == TDOT || ltok == IDENT) {
            ParList();
        }
        check(RPAR);
        Block();
        check(END);
    }

    private void ParList() {
        if (ltok == TDOT) {
            scan();
        } else {
            check(IDENT);
            while (ltok == COMMA && lla.type() != TDOT) {
                scan();
                check(IDENT);
            }
            if (ltok == COMMA) {
                scan();
                check(TDOT);
            }
        }
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
