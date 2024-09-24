package dev.asdf00.jluavm.parsing;

import java.util.EnumSet;

import static dev.asdf00.jluavm.parsing.TokenType.*;

public class Parser {
    private TokenType ltok = EOF;  // TokenType of la
    private Token cur;  // current token
    private Token la;  // lookahead token

    private void ensure(boolean condition) {

    }

    private void check(TokenType type) {

    }

    private void scan() {

    }

    private void Chunk() {
        Block();
    }

    private void Block() {
        while (STAT_START.contains(ltok)) {
            Stat();
        }
        if (ltok == RETURN) {
            scan();

        }
    }

    private static final EnumSet<TokenType> STAT_START = EnumSet.of(IDENT, LPAR, DCOLON, BREAK, GOTO, DO, WHILE, REPEAT, IF, FOR, FUNCTION, LOCAL);
    private void Stat() {
        switch (ltok) {
            case SEMICOLON -> {
                // nop
                scan();
            }
            case DCOLON -> {
                // label
                scan();
                check(IDENT);
                check(DCOLON);
            }
            case BREAK -> {
                scan();
            }
            case GOTO -> {
                scan();
                check(IDENT);
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
                // VarList or FunctionCall
                VarOrFunctionCall();
                if (VAR_END.contains(cur.type())) {
                    // previous was Var
                    while (ltok == COMMA) {
                        scan();
                        VarOrFunctionCall();
                        ensure(VAR_END.contains(cur.type()));
                    }
                    check(ASSIGN);
                    ExpList();
                } else {
                    // previous was FunctionCall

                }
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

    private static final EnumSet<TokenType> VAR_END = EnumSet.of(IDENT, RBRAK);

    /**
     * After this method returns, it can be checked if this method read a Var or a FunctionCall by checking if
     * {@code VAR_LIST_END.contains(cur.type())}.
     */
    private void VarOrFunctionCall() {
        if (ltok == IDENT) {
            scan();
        } else {
            check(LPAR);
            Exp();
            check(RPAR);
        }
        loop: for (;;) {
            switch (ltok) {
                case LBRAK -> {
                    scan();
                    check(IDENT);
                    check(RBRAK);
                }
                case DOT -> {
                    scan();
                    check(IDENT);
                }
                case COLON, LPAR, LBRAC, LITERAL_STRING -> {
                    if (ltok == COLON) {
                        scan();
                        check(IDENT);
                    }
                    Args();
                }
                default -> {
                    break loop;
                }
            }
        }
    }

    private void ExpList() {
        Exp();
        while (ltok == COMMA) {
            scan();
            Exp();
        }
    }

    private static final EnumSet<TokenType> EXP_START = EnumSet.of(NIL, FALSE, TRUE, NUMERAL, LITERAL_STRING, TDOT, FUNCTION, LPAR, IDENT);
    private void Exp() {
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
                // FunctionDef
                scan();
                FuncBody();
            }

        }
    }

    private void PrefixExp() {

    }

    private void FunctionCall() {

    }


    private static final EnumSet<TokenType> ARGS_START = EnumSet.of(LPAR, LBRAC, LITERAL_STRING);
    private void Args() {

    }

    private void FuncBody() {

    }

    private void ParList() {

    }

    private void TableConstructor() {

    }

    private void FieldList() {

    }

    private void Field() {
        if (ltok == LBRAK) {
            scan();
            Exp();
            check(RBRAK);
            check(ASSIGN);
            Exp();
        }
    }

    private void FieldSep() {
        if (ltok == COMMA || ltok == SEMICOLON) {
            scan();
        }
    }

    private void BinOp0() {
        // or
    }

    private void BinOp1() {
        // and
    }

    private void BinOp2() {
        // < > <= >= ~= ==
    }

    private void BinOp3() {
        // binor |
    }

    private void BinOp4() {
        // negate ~
    }

    private void BinOp5() {
        // binand &
    }

    private void BinOp6() {
        // << >>
    }

    private void BinOp7() {
        // ..
    }

    private void BinOp8() {
        // + -
    }

    private void BinOp9() {
        // * / // %
    }

    private void UnOp() {

    }

    private void BinOp10() {
        // ^

    }
}
