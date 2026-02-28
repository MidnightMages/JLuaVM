package dev.asdf00.jluavm.runtime.stdlib;

import dev.asdf00.jluavm.api.functions.AtomicLuaFunction;
import dev.asdf00.jluavm.api.functions.MixedStateFunctionRegistry;
import dev.asdf00.jluavm.exceptions.LuaJavaError;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.stdlib.patternMatching.PatternMatchingImpl;
import dev.asdf00.jluavm.runtime.types.LuaObject;

import java.util.Locale;
import java.util.regex.Pattern;

import static dev.asdf00.jluavm.runtime.types.LuaObject.Types.*;
import static dev.asdf00.jluavm.runtime.utils.RTUtils.funcArgAnyTypeError;
import static dev.asdf00.jluavm.runtime.utils.RTUtils.funcArgTypeError;

public class LString {

    private static final String STRING_PREFIX = "string.";

    public static LuaObject createExtTable(LuaObject stringTable) {
        var tblClone = LuaObject.wrapMap(stringTable.asMap().clone());
        // remove string.char (int -> string) as an extension function of strings
        tblClone.set("char", LuaObject.NIL);
        return tblClone;
    }

    public static void registerStdString(MixedStateFunctionRegistry registry) {
        /* TODO, add the following ones: https://www.lua.org/manual/5.4/manual.html#6.4
        string.dump(function [, strip])
        string.find(s, pattern [, init [, plain]])
        string.format(formatstring, ···)
        string.gmatch(s, pattern [, init])
        string.gsub(s, pattern, repl [, n])
        string.match(s, pattern [, init])
        string.pack(fmt, v1, v2, ···)
        string.packsize(fmt)
        string.unpack(fmt, s [, pos])
         */

        registry.register(STRING_PREFIX + "byte",
                AtomicLuaFunction.vaForOneResult(registry, (vm, va) -> {
                    if (va.length < 1 || !va[0].isType(ARITHMETIC)) {
                        vm.error(funcArgTypeError("string.byte", 0, va.length > 0 ? va[0] : null, "string"));
                        return null;
                    }

                    if (va.length > 1 && !va[1].hasLongRepr()) {
                        vm.error(funcArgTypeError("string.byte", 1, va[1], "integer"));
                        return null;
                    }

                    if (va.length > 2 && !va[2].isNil() && !va[2].hasLongRepr()) {
                        vm.error(funcArgAnyTypeError("string.byte", 2, va[2], "integer", "nil", "nothing"));
                        return null;
                    }
                    return sub(vm, va);
                }));

        registry.register(STRING_PREFIX + "char",
                AtomicLuaFunction.vaForOneResult(registry, (vm, va) -> {
                    var r2 = new StringBuilder(va.length);
                    for (int i = 0; i < va.length; i++) {
                        if (va[i].isNumberCoercible()) {
                            if (va[i].hasLongRepr()) {
                                r2.append(va[i].asLong());
                            } else {
                                vm.error(funcArgTypeError("string.char", i, va[i], "integer"));
                                return null;
                            }
                        } else {
                            vm.error(funcArgTypeError("string.char", i, va[i], "integer"));
                            return null;
                        }
                    }
                    return LuaObject.of(r2.toString());
                }));

        registry.register(STRING_PREFIX + "match",
                AtomicLuaFunction.vaForManyResults(registry, (vm, va) -> {
                    var s = va.length > 0 ? va[0] : null;
                    var pattern = va.length > 1 ? va[1] : null;
                    var init = va.length > 2 ? va[2] : LuaObject.of(1); // startIndex
                    if (s == null || !s.isType(NUMBER | STRING)) {
                        vm.error(funcArgAnyTypeError("string.match", 0, s, "string", "number"));
                        return null;
                    }
                    if (pattern == null || !pattern.isType(NUMBER | STRING)) {
                        vm.error(funcArgAnyTypeError("string.match", 1, pattern, "string", "number"));
                        return null;
                    }
                    if (!init.hasLongRepr()) {
                        vm.error(funcArgTypeError("string.match", 1, pattern, "integer"));
                        return null;
                    }

                    var inputStr = s.getString();
                    var searchStartIndex = init.asLong();
                    if (searchStartIndex < 0) // negatives go from the back, -1 being the last letter
                        searchStartIndex += inputStr.length() + 1;
                    else if (searchStartIndex == 0) { // startpos 0 is interpreted as startpos 1, aka the default value
                        searchStartIndex++;
                    }
                    searchStartIndex--; // convert to java index
                    if (searchStartIndex < 0) // if it is too small, make it start at 0
                        searchStartIndex = 0;

                    if (searchStartIndex > Integer.MAX_VALUE)
                        throw new LuaJavaError("searchStartIndex too large");

                    return PatternMatchingImpl.lua_match(inputStr, pattern.getString(), (int)searchStartIndex);
                }));

        registry.register(STRING_PREFIX + "gmatch",
                AtomicLuaFunction.vaForOneResult(registry, (vm, va) -> {
                    var s = va.length > 0 ? va[0] : null;
                    var pattern = va.length > 1 ? va[1] : null;
                    var init = va.length > 2 ? va[2] : LuaObject.of(1); // startIndex
                    if (s == null || !s.isType(NUMBER | STRING)) {
                        vm.error(funcArgAnyTypeError("string.gmatch", 0, s, "string", "number"));
                        return null;
                    }
                    if (pattern == null || !pattern.isType(NUMBER | STRING)) {
                        vm.error(funcArgAnyTypeError("string.gmatch", 1, pattern, "string", "number"));
                        return null;
                    }
                    if (!init.hasLongRepr()) {
                        vm.error(funcArgTypeError("string.gmatch", 2, pattern, "integer"));
                        return null;
                    }

                    var inputStr = s.getString();
                    var matchStartPos = init.asLong();
                    if (matchStartPos < 0) // negatives go from the back, -1 being the last letter
                        matchStartPos += inputStr.length() + 1;
                    else if (matchStartPos == 0) { // startpos 0 is interpreted as startpos 1, aka the default value
                        matchStartPos++;
                    }
                    matchStartPos--; // convert to java index
                    if (matchStartPos < 0) // if it is too small, make it start at 0
                        matchStartPos = 0;

                    if (matchStartPos > Integer.MAX_VALUE)
                        throw new LuaJavaError("searchStartIndex too large");


                    // ^ is treated literally in gmatch (at the beginning)

                    String patternString = pattern.getString();
                    if (patternString.startsWith("^"))
                        patternString = "%" + patternString;

                    final String patternString_f = patternString;
                    int[] matchStartPosClosured = new int[] {(int)matchStartPos};
                    return AtomicLuaFunction.vaForManyResults(registry, (vm2, args) -> {
                        if (matchStartPosClosured[0] >= inputStr.length())
                            return new LuaObject[]{};

                        var extRes = PatternMatchingImpl.lua_match_extended(inputStr, patternString_f, matchStartPosClosured[0]);
                        if (!extRes.res().success()) {
                            // definitely out of bounds to block future calls in a cheap manner
                            matchStartPosClosured[0] = Integer.MAX_VALUE;
                        } else {
                            var res = extRes.res();
                            matchStartPosClosured[0] = res.end();
                            if (res.start() == res.end())
                                matchStartPosClosured[0]++;
                        }
                        return extRes.matchResult();
                    }).obj();
                }));

        registry.register(STRING_PREFIX + "gsub",
                AtomicLuaFunction.vaForManyResults(registry, (vm, va) -> {
                    var s = va.length > 0 ? va[0] : null;
                    var pattern = va.length > 1 ? va[1] : null;
                    var repl = va.length > 2 ? va[2] : null;
                    var nReplacements = va.length > 3 ? va[3] : LuaObject.NIL; // max number of replacements
                    if (s == null || !s.isType(NUMBER | STRING)) {
                        vm.error(funcArgAnyTypeError("string.gsub", 0, s, "string", "number"));
                        return null;
                    }
                    if (pattern == null || !pattern.isType(NUMBER | STRING)) {
                        vm.error(funcArgAnyTypeError("string.gsub", 1, pattern, "string", "number"));
                        return null;
                    }

                    // TODO support tables and functions for replacements (see ref manual https://www.lua.org/manual/5.4/manual.html#pdf-string.gsub )
                    if (repl == null || !repl.isType(NUMBER | STRING)) {
                        vm.error(funcArgAnyTypeError("string.gsub", 2, pattern, "string", "number"));
                        return null;
                    }
                    if (!nReplacements.isNil() && !nReplacements.hasLongRepr()) {
                        vm.error(funcArgTypeError("string.gsub", 3, pattern, "integer"));
                        return null;
                    }

                    String currentInputString = s.asString();
                    String replacementString = repl.asString();
                    String patternString = pattern.asString();
//                    if (patternString.startsWith("^"))
//                        patternString = "%"+patternString;

                    StringBuilder rv = new StringBuilder();
                    var maxReplacements = nReplacements.isNil() ? Integer.MAX_VALUE : nReplacements.asLong();
                    int numberOfTotalMatchesThatOccurred = 0;
                    int currentStartPos = 0;
                    int lastNonZeroMatchPos = -1;
                    var regexPattern = Pattern.compile("%(\\d|%)", Pattern.DOTALL);
                    for (int i = 0; i < maxReplacements; i++) {
                        var extRes = PatternMatchingImpl.lua_match_extended(currentInputString, patternString, currentStartPos);
                        var findRes = extRes.res();
                        if (!findRes.success()) { // no more matches --> finish up and return
                            break;
                        }


                        var lazinessPreservedPattern = patternString.replace(")", "").replaceAll("%\\d","");
                        // match is out of bounds and not zerolength right at the end
                        if (currentStartPos >= currentInputString.length() && (
                                findRes.matchLength() != 0 ||
                                !(lazinessPreservedPattern.endsWith("-") ||
                                  lazinessPreservedPattern.endsWith("?") )           // this is hacky as it could also contain %? or %- respectively, and that wouldnt count
                        )) {
                            break;
                        }

                        Runnable applySubstitution = () -> {
                            String currentReplacement = regexPattern.matcher(replacementString)
                                    .replaceAll(mr -> {
                                        if (mr.group().equals("%%"))
                                            return "%";
                                        var captureGroupId = Integer.parseInt(mr.group().substring(1));

                                        // 0 is the whole match always, but so is 1 if the pattern does not contian any captures
                                        if (captureGroupId == 0 ||
                                            captureGroupId == 1 && findRes.captures().length == 0) {
                                            return currentInputString.substring(findRes.start(), findRes.end());
                                        }
                                        var cap = findRes.captures()[captureGroupId - 1];
                                        return cap != null ? cap : "";
                                    });

                            rv.append(currentReplacement);
                        };

                        if (findRes.matchLength() == 0) { // this is a zero len match
                            if (currentStartPos >= currentInputString.length()) {
                                applySubstitution.run();
                                numberOfTotalMatchesThatOccurred++;
                                break;
                            }

                            if (lastNonZeroMatchPos == findRes.start()) { // skip it if it is right after another match
                                rv.append(currentInputString.charAt(currentStartPos));
                                currentStartPos++;
                                continue;
                            }
                        } else {
                            lastNonZeroMatchPos = findRes.end();
                        }

                        // there was a match
                        numberOfTotalMatchesThatOccurred++;

                        // add the original string thats before our match
                        if (currentStartPos != findRes.start())
                            rv.append(currentInputString, currentStartPos, findRes.start());

                        // then add the replacement
                        // TODO, here query the replacement function/table for a value to put in. For now we just use the supplied string
                        applySubstitution.run();

                        // and advance the next matchpos
                        if (findRes.end() == currentStartPos) {
                            rv.append(currentInputString.charAt(currentStartPos));
                            currentStartPos++;
                        } else {
                            currentStartPos = findRes.end();
                        }
                    }
                    // no more matches --> finish up and return
                    if (currentStartPos < currentInputString.length())
                        rv.append(currentInputString.substring(currentStartPos));

                    return new LuaObject[]{LuaObject.of(rv.toString()), LuaObject.of(numberOfTotalMatchesThatOccurred)};
                }));

        registry.register(STRING_PREFIX + "len",
                AtomicLuaFunction.forOneResult(registry, (vm, s) -> {
                    if (!s.isType(ARITHMETIC)) {
                        vm.error(funcArgTypeError("string.len", 0, s, "string"));
                        return null;
                    }
                    return LuaObject.of(s.asString().length());
                }));

        registry.register(STRING_PREFIX + "lower",
                AtomicLuaFunction.forOneResult(registry, (vm, s) -> {
                    if (!s.isType(ARITHMETIC)) {
                        vm.error(funcArgTypeError("string.lower", 0, s, "string"));
                        return null;
                    }
                    return LuaObject.of(s.asString().toLowerCase(Locale.US));
                }));

        registry.register(STRING_PREFIX + "rep",
                AtomicLuaFunction.vaForOneResult(registry, (vm, va) -> {
                    if (va.length < 1 || !va[0].isType(ARITHMETIC)) {
                        vm.error(funcArgTypeError("string.rep", 0, va.length > 0 ? va[0] : null, "string"));
                        return null;
                    }

                    if (va.length < 2 || !va[1].hasLongRepr()) {
                        vm.error(funcArgTypeError("string.rep", 1, va[1], "integer"));
                        return null;
                    }

                    //noinspection All
                    assert va.length >= 2;

                    var sep = va.length > 2 ? va[2].asString() : "";
                    if (va.length > 2 && !va[2].isType(ARITHMETIC | BOOLEAN | NIL)) {
                        vm.error(funcArgAnyTypeError("string.rep", 2, va[2],
                                "string", "boolean", "integer", "nil", "nothing"));
                        return null;
                    }
                    var cnt = (int) (va[1].asLong());
                    if (cnt <= 0)
                        return LuaObject.of("");
                    var s = va[0].asString();
                    return LuaObject.of(s + (sep + s).repeat(cnt - 1));
                }));

        registry.register(STRING_PREFIX + "reverse",
                AtomicLuaFunction.forOneResult(registry, (vm, s) -> {
                    if (!s.isType(ARITHMETIC)) {
                        vm.error(funcArgTypeError("string.reverse", 0, s, "string"));
                        return null;
                    }
                    return LuaObject.of(new StringBuilder(s.asString()).reverse().toString());
                }));

        registry.register(STRING_PREFIX + "sub",
                AtomicLuaFunction.vaForOneResult(registry, LString::sub));

        registry.register(STRING_PREFIX + "upper",
                AtomicLuaFunction.forOneResult(registry, (vm, s) -> {
                    if (!s.isType(ARITHMETIC)) {
                        vm.error(funcArgTypeError("string.upper", 0, s, "string"));
                        return null;
                    }
                    return LuaObject.of(s.asString().toUpperCase(Locale.US));
                }));
    }

    // =================================================================================================================
    //  static function definitions
    // =================================================================================================================
    private static LuaObject sub(LuaVM_RT vm, LuaObject[] va) {// TODO add unittests
        if (va.length < 1 || !va[0].isType(ARITHMETIC)) {
            vm.error(funcArgTypeError("string.sub", 0, va.length > 0 ? va[0] : null, "string"));
            return null;
        }

        if (va.length < 2 || !va[1].hasLongRepr()) {
            vm.error(funcArgTypeError("string.sub", 1, va[1], "integer"));
            return null;
        }

        //noinspection All
        assert va.length >= 2;

        if (va.length > 2 && !va[2].isNil() && !va[2].hasLongRepr()) {
            vm.error(funcArgAnyTypeError("string.sub", 2, va[2], "integer", "nil", "nothing"));
            return null;
        }
        var s = va[0].asString();
        var i = va[1].asLong();

        if (i < 0) i += s.length() + 1; // i=-1 must be same as string length, i.e (-1)+1+s.length() = s.length()
        if (i < 1) i = 1;

        var j = va.length > 2 && !va[2].isNil() ? va[2].asLong() : -1;
        if (j < 0) j += s.length() + 1; // i=-1 must be same as string length, i.e (-1)+1+s.length() = s.length()
        if (j > s.length()) j = s.length();
        if (i > j)
            return LuaObject.of("");

        return LuaObject.of(s.substring((int) i - 1, (int) j));
    }

    // ported from https://github.dev/lua/lua/blob/b4b616bdf2beb161b89930cc71a06936e8531b2c/lapi.c
    private static class StringMatcher {
        private static final int LUA_MAXCAPTURES = 32;
        private static final int MAXCCALLS = 200;


        public StringMatcher(String s, int startPos) {

        }

        public StringPtr match(MatchState ms, String s, String p) {
            return match(ms, new StringPtr(s), new StringPtr(p));
        }

        private char cast_uchar(char x) { // NOP
            return x;
        }

        public StringPtr match(MatchState ms, StringPtr sPtr, StringPtr pPtr) {
            if (ms.matchDepth-- == 0) {
                throw new RuntimeException("Pattern too complex");
            }
            init:
            while (true) { // init: jumplabel; continue jumps to there; break exits
                if (pPtr.offset != ms.pEnd.offset) {
                    boolean gotoDefault = false;
                    switch (pPtr.deref()) {
                        case '(' -> {
                            if (pPtr.deref(1) == ')') {
                                sPtr = start_capture(ms, sPtr, pPtr.withOffset(2), CAP_CONSTS.CAP_POSITION.getValue());
                            } else {
                                sPtr = start_capture(ms, sPtr, pPtr.withOffset(1), CAP_CONSTS.CAP_UNFINISHED.getValue());
                            }
                        }
                        case ')' -> sPtr = end_capture(ms, sPtr, pPtr.withOffset(1));
                        case '$' -> {
                            if (pPtr.offset + 1 != ms.pEnd.offset) {
                                gotoDefault = true;
                            } else {
                                sPtr = (sPtr.offset == ms.srcEnd.offset) ? sPtr : null;
                            }
                        }
                        case '%' -> { // aka L_ESC
                            switch (pPtr.deref(1)) {
                                case 'b' -> {
                                    sPtr = matchbalance(ms, sPtr, pPtr.withOffset(2));
                                    if (pPtr != null) {
                                        pPtr.offsetInplace(4);
                                        continue init;
                                    }
                                }
                                case 'f' -> {
                                    pPtr.offsetInplace(2);
                                    if (pPtr.deref() != '[') {
                                        throw new RuntimeException("missing '[' after %%f in pattern");
                                    }
                                    StringPtr epPtr = classend(ms, pPtr);
                                    char previous = (sPtr.offset == ms.srcInit.offset) ? '\0' : sPtr.deref(-1);
                                    if (!matchbracketclass(cast_uchar(previous), pPtr, epPtr.withOffset(-1)) &&
                                        matchbracketclass(cast_uchar(sPtr.deref()), pPtr, epPtr.withOffset(-1))) {
                                        pPtr = epPtr;
                                        continue init;
                                    }
                                    sPtr = null;
                                }
                                case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                                    sPtr = match_capture(ms, sPtr, cast_uchar(pPtr.deref(1)));
                                    if (sPtr != null) {
                                        pPtr.offsetInplace(2);
                                        continue init;
                                    }
                                }
                                default -> gotoDefault = true;
                            }
                        }
                        default -> gotoDefault = true;
                    }
                    if (gotoDefault) {
                        final StringPtr epPtr = classend(ms, pPtr); // optional suffix
                        if (!singlematch(ms, sPtr, pPtr, epPtr)) {
                            var e = epPtr.deref();
                            if (e == '*' || e == '?' || e == '-') {
                                pPtr = epPtr.withOffset(1);
                                continue init; // return match(ms, s, ep + 1)
                            } else {
                                sPtr = null;
                            }
                        } else {
                            switch (epPtr.deref()) {
                                case '?': {
                                    StringPtr resPtr = match(ms, sPtr.withOffset(1), epPtr.withOffset(1));
                                    if (resPtr != null) {
                                        sPtr = resPtr;
                                    } else {
                                        pPtr = epPtr.withOffset(1);
                                        continue init;
                                    }
                                    break;
                                }
                                case '+':
                                    sPtr.offsetInplace(1); // FALLTHROUGH
                                case '*': {
                                    sPtr = max_expand(ms, sPtr, pPtr, epPtr);
                                    break;
                                }
                                case '-': {
                                    sPtr = min_expand(ms, sPtr, pPtr, epPtr);
                                    break;
                                }
                                default: {
                                    sPtr.offsetInplace(1);
                                    pPtr = epPtr;
                                    continue init;
                                }
                            }
                        }
                    }
                }
                break;
            }
            ms.matchDepth++;
            return sPtr;
        }

        private char check_capture(MatchState ms, char lc) {
            int l = lc;
            l -= '1';
            if (l < 0 || l >= ms.level || ms.capture[l].len == CAP_CONSTS.CAP_UNFINISHED.getValue()) {
                throw new RuntimeException("invalid capture index %%%d".formatted(l+1));
            }
            //noinspection ConstantValue
            assert l >= 0;
            return (char)l;
        }

        private StringPtr match_capture(MatchState ms, StringPtr sPtr, char l) {
            l = check_capture(ms, l);
            var len = ms.capture[l].len;
            if (ms.srcEnd.offset - sPtr.offset >= len &&
                ms.capture[l].init.s.substring(0, len).equals(sPtr.s.substring(0, len))) {
                return sPtr.withOffset(len);
            } else {
                return null;
            }
        }

        private StringPtr matchbalance(MatchState ms, StringPtr sPtr, StringPtr pPtr) {
            if (pPtr.offset >= ms.pEnd.offset - 1) {
                throw new RuntimeException("malformed pattern (missing arguments to '%%b'");
            }
            if (sPtr.deref() != pPtr.deref()) {
                return null;
            } else {
                sPtr = sPtr.clone();
                int b = pPtr.deref();
                int e = pPtr.deref(1);
                int cont = 1;
                while (true) {
                    sPtr.offsetInplace(1);
                    // while cond
                    if (sPtr.offset < ms.srcEnd.offset) {
                        if (sPtr.deref() == e) {
                            if (--cont == 0) {
                                return sPtr.withOffset(1);
                            }
                        } else if (sPtr.deref() == b) {
                            cont++;
                        }
                    } else {
                        break;
                    }
                }
            }
            return null;
        }

        private boolean matchbracketclass(char c, StringPtr pPtr, StringPtr ecPtr) {
            pPtr = pPtr.clone();
            boolean sig = true;
            if (pPtr.deref(1) == '^') {
                sig = false;
                pPtr.offsetInplace(1);
            }
            while (true) {
                pPtr.offsetInplace(1);
                if (pPtr.offset < ecPtr.offset) {
                    if (pPtr.deref() == '%') {
                        pPtr.offsetInplace(1);
                        if (match_class(c, pPtr.deref()))
                            return sig;
                    } else if ((pPtr.deref(1) == '-') && (pPtr.offset + 2 < ecPtr.offset)) {
                        pPtr.offsetInplace(2);
                        if (pPtr.deref(-2) <= c && c <= pPtr.deref())
                            return sig;
                    } else if (pPtr.deref() == c)
                        return sig;
                } else {
                    break;
                }
            }
            return !sig;
        }

        private boolean match_class(char c, char cl) {
            boolean res = switch (Character.toLowerCase(cl)) {
                case 'a' -> c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z';
                case 'c' -> c <= 0x1F || c == 0x7F;
                case 'd' -> c >= '0' && c <= '9';
                case 'g' -> c >= '!' && c <= '~';
                case 'l' -> c >= 'a' && c <= 'z';
                case 'p' ->
                        (c >= '!' && c <= '~') && !(c >= '0' && c <= '9') && !(c >= 'A' && c <= 'Z') && !(c >= 'a' && c <= 'z');
                case 's' -> c >= '\t' && c <= '\r' || c == ' ';
                case 'u' -> c >= 'A' && c <= 'Z';
                case 'w' -> c >= '0' && c <= '9' || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z';
                case 'x' -> c >= '0' && c <= '9' || c >= 'A' && c <= 'F' || c >= 'a' && c <= 'f';
                case 'z' -> c == '\0';
                default -> cl == c;
            };
            //noinspection SimplifiableConditionalExpression
            return Character.isLowerCase(cl) ? res : !res;
        }

        private boolean singlematch(MatchState ms, StringPtr sPtr, StringPtr pPtr, StringPtr epPtr) {
            if (sPtr.offset >= ms.srcEnd.offset)
                return false;
            else {
                var c = sPtr.deref();
                return switch (pPtr.deref()) {
                    case '.' -> true;
                    case '%' -> match_class(c, pPtr.deref(1));
                    case '[' -> matchbracketclass(c, pPtr, epPtr.withOffset(-1));
                    default -> pPtr.deref() == c;
                };
            }
        }

        private StringPtr min_expand(MatchState ms, StringPtr sPtr, StringPtr pPtr, StringPtr epPtr) {
            while (true) {
                var res = match(ms, sPtr, epPtr.withOffset(1));
                if (res != null)
                    return res;
                else if (singlematch(ms, sPtr, pPtr, epPtr))
                    sPtr = sPtr.withOffset(1);
                else
                    return null;
            }
        }

        private StringPtr max_expand(MatchState ms, StringPtr sPtr, StringPtr pPtr, StringPtr epPtr) {
            int i = 0;
            while (singlematch(ms, sPtr.withOffset(i), pPtr, epPtr)) {
                i++;
                while (i >= 0) {
                    var res = match(ms, sPtr.withOffset(i), epPtr.withOffset(1));
                    if (res != null)
                        return res;
                    i--;
                }
            }
            return null;
        }

        private enum CAP_CONSTS {
            CAP_UNFINISHED(-1),
            CAP_POSITION(-2);

            private final int value;

            CAP_CONSTS(int value) {
                this.value = value;
            }

            public int getValue() {
                return value;
            }
        }

        private StringPtr start_capture(MatchState ms, StringPtr sPtr, StringPtr pPtr, int what) {
            int level = ms.level;
            if (level >= LUA_MAXCAPTURES)
                throw new RuntimeException("too many captures");
            if (ms.capture[level] == null)
                ms.capture[level] = new Capture();
            ms.capture[level].init = sPtr.clone();
            ms.capture[level].len = what;
            ms.level++;
            StringPtr res = match(ms, sPtr, pPtr);
            if (res == null)
                ms.level--;
            return res;
        }

        private StringPtr end_capture(MatchState ms, StringPtr sPtr, StringPtr pPtr) {
            int l = capture_to_close(ms);
            ms.capture[l].len = sPtr.offset - ms.capture[l].init.offset;
            StringPtr res = match(ms, sPtr, pPtr);
            if (res == null)
                ms.capture[l].len = CAP_CONSTS.CAP_UNFINISHED.getValue();
            return res;
        }

        private int capture_to_close(MatchState ms) {
            int level = ms.level;
            for (level--; level >= 0; level--) {
                if (ms.capture[level].len == CAP_CONSTS.CAP_UNFINISHED.getValue())
                    return level;
            }
            throw new RuntimeException("invalid pattern capture");
        }

        private StringPtr classend(MatchState ms, StringPtr pPtr) {
            switch (pPtr.derefThenIncrement()) {
                case '%' -> {
                    if (pPtr.offset == ms.pEnd.offset) {
                        throw new RuntimeException("malformed pattern (ends with '%%");
                    }
                    return pPtr.withOffset(1);
                }
                case '[' -> {
                    if (pPtr.deref() == '^')
                        pPtr.offsetInplace(1);
                    do {
                        if (pPtr.offset == ms.pEnd.offset)
                            throw new RuntimeException("malformed pattern (missing ']')");
                        if (pPtr.derefThenIncrement() == '%' && pPtr.offset < ms.pEnd.offset)
                            pPtr.offsetInplace(1);
                    } while (pPtr.deref() != ']');
                    return pPtr.withOffset(1);
                }
                default -> {
                    return pPtr;
                }
            }
        }


        private static final class Capture {
            public StringPtr init;
            public int len;

            private Capture() {
                this.init = null;
                this.len = Integer.MIN_VALUE;
            }
        }

        private class MatchState {
            int matchDepth;
            StringPtr srcInit;
            StringPtr srcEnd;
            StringPtr pEnd;
            int level; // TODO init
            Capture[] capture; // TODO init

            // = prepstate()
            public MatchState(String src, String pattern) {
                matchDepth = MAXCCALLS;
                srcInit = new StringPtr(src, 0);
                srcEnd = new StringPtr(src, src.length());
                pEnd = new StringPtr(pattern, pattern.length());
            }
        }

        private static class StringPtr {
            final String s;
            int offset;

            public StringPtr(String s, int offset) {
                this.s = s;
                this.offset = offset;
            }

            public StringPtr(String s) {
                this(s, 0);
            }

            public void offsetInplace(int offset) {
                this.offset += offset;
            }

            public StringPtr withOffset(int offset) {
                return new StringPtr(s, this.offset + offset);
            }

            public char deref() {
                return deref(0);
            }

            public char deref(int offset) {
                var offsettedPos = this.offset + offset;
                if (offsettedPos == s.length())
                    return '\0';
                return s.charAt(offsettedPos);
            }

            @SuppressWarnings("MethodDoesntCallSuperMethod")
            public StringPtr clone() {
                return new StringPtr(s, offset);
            }

            public char derefThenIncrement() {
                var rv = deref();
                offsetInplace(1);
                return rv;
            }
        }
    }
}
