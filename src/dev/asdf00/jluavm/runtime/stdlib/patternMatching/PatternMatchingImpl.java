package dev.asdf00.jluavm.runtime.stdlib.patternMatching;

import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;
import dev.asdf00.jluavm.exceptions.LuaJavaError;
import dev.asdf00.jluavm.runtime.types.LuaObject;

import javax.management.openmbean.InvalidOpenTypeException;
import java.util.Arrays;
import java.util.regex.Pattern;

// ported from https://github.com/jnwhiteh/go-luapatterns/blob/master/luapatterns/luapatterns.go#L49
public class PatternMatchingImpl {

    static final String SPECIALS = "^$*+?.([%-";
    static final int LUA_MAXCAPTURES = 32;
    static final int CAP_UNFINISHED = -1;

    private static int lmemfind(String s1, String s2) {
        return s1.indexOf(s2);
    }

    private static String get_onecapture(MatchState ms, int i, String s, String e) {
        if (i >= ms.level) {
            if (i == 0) {
                return s.substring(0, s.length() - e.length());
            } else {
                // error: invalid capture index
                return null;
            }
        } else {
            int l = ms.capture[i].len;
            if (l == CAP_UNFINISHED) {
                // error: unfinished capture
                return null;
            } else {
                return ms.capture[i].src.substring(0, l);
            }
        }
    }

    public static FindResult find(String s, String p, boolean plain) {
        String finalP = p;
        if (plain || SPECIALS.chars().mapToLong(special -> finalP.contains(Character.toString(special)) ? 1 : 0).max().orElse(0) == 0) {
            var index = lmemfind(s, p);
            if (index != -1) {
                return new FindResult(true, index, index + p.length(), null);
            } else {
                return new FindResult(false, -1, -1, null);
            }
        }

        var anchor = false;
        if (p.charAt(0) == '^') {
            p = p.substring(1);
            anchor = true;
        }

        MatchState ms = new MatchState();
        ms.capture = new Capture[LUA_MAXCAPTURES];

        int init = 0;
        while (true) {
            String res = match(ms, s.substring(init), p);
            if (res != null) {
                int start = init;
                int end = s.length() - res.length();

                var captures = new String[LUA_MAXCAPTURES];

                int nlevels = (ms.level == 0 && s.length() > 0) ? 1 : ms.level;

                for (int i = 0; i < nlevels; i++) {
                    captures[i] = get_onecapture(ms, i, s.substring(start), res);
                }

                return new FindResult(true, start, end, Arrays.copyOfRange(captures, 0, nlevels));
            } else if (s.length() - init == 0 || anchor) {
                break;
            }

            init++;
        }

        return new FindResult(false, -1, -1, null);
    }

    private static boolean match_class(char c, char cl) {
        var cllower = Character.toLowerCase(cl);
        var wasLower = cl == cllower;
        boolean res;

        switch (cllower) {
            case 'a' -> res = Character.isAlphabetic(c);
            case 'c' -> res = Character.isISOControl(c);
            case 'd' -> res = Character.isDigit(c);
            case 'g' -> res = c <= 255 ? ('!' <= c && c <= '~') : !Character.isWhitespace(c);
            case 'l' -> res = Character.isLowerCase(c);
            case 'p' -> res = Pattern.matches("\\p{IsPunctuation}", Character.toString(c));
            case 's' -> res = Character.isWhitespace(c);
            case 'u' -> res = Character.isUpperCase(c);
            case 'w' ->
                    res = Character.isDigit(c) || Character.isUpperCase(c) || Character.isLowerCase(c); // [0-9A-Za-z] in vanilla lua
            case 'x' ->
                    res = Character.isDigit(c) || ('A' <= Character.toUpperCase(c) && Character.toUpperCase(c) <= 'F'); // [0-9A-Fa-f] in vanilla lua
            case 'z' -> res = (c == '\0');
            default -> res = cl == c;
        }

        if (wasLower)
            return res;

        return !res;
    }

    private static boolean matchbracketclass(char c, String p, String ec) {
        boolean sig = true;
        if (p.charAt(1) == '^') {
            sig = false;
            p = p.substring(1);
        }
        for (p = p.substring(1); p.length() > ec.length(); p = p.substring(1)) {
            if (p.charAt(0) == '%') {
                p = p.substring(1);
                if (match_class(c, p.charAt(0))) {
                    return sig;
                }
            } else if (p.charAt(1) == '-' && (p.length() - 2 > ec.length())) {
                if (p.charAt(0) <= c && c <= p.charAt(2)) {
                    return sig;
                }
            } else if (p.charAt(0) == c) {
                return sig;
            }
        }
        return !sig;
    }

    private static boolean singlematch(char c, String p, String ep) {
        switch (p.charAt(0)) {
            case '.' -> {
                return true;
            }
            case '%' -> {
                return match_class(c, p.charAt(1));
            }
            case '[' -> {
                ep = p.substring(p.length() - ep.length() - 1);
                return matchbracketclass(c, p, ep);
            }
            default -> {
                return p.charAt(0) == c;
            }
        }
    }

    // Returns the portion of the source string that matches the balance pattern
    // specified, where b is the start and e is the end of the balance pattern.
    private static String matchbalance(MatchState ms, String s, String p) {
        if (p.length() <= 1) {
            throw new LuaJavaError("error: unbalanced pattern");
        }
        if (s.charAt(0) != p.charAt(0)) {
            return null;
        } else {
            char b = p.charAt(0);
            char e = p.charAt(1);
            int cont = 1;

            // ms.src_end in the original C source is a pointer to the end of the
            // source string (whatever that means specifically). This loop wants to
            // ensure that s remains less than this pointer. Since we're not
            // dealing with pointers, we should be able to just run the loop until
            // s runs out.

            for (s = s.substring(1); s.length() > 0; s = s.substring(1)) {
                if (s.charAt(0) == e) {
                    cont--;
                    if (cont == 0) {
                        return s.substring(1);
                    }
                } else if (s.charAt(0) == b) {
                    cont++;
                }
            }
        }
        throw new LuaJavaError("error: string ends out of balance");
    }

    private static String max_expand(MatchState ms, String s, String p, String ep) {
        // Run through the string to find the maximum number of matches that are
        // possible for the pattern item.
        int i;
        for (i = 0; i < s.length() && singlematch(s.charAt(i), p, ep); i++) {
        }

        // Try to match with maximum reptitions
        while (i > 0) {
            var res = match(ms, s.substring(i), ep.substring(1));
            if (res != null) {
                return res;
            } else {
                // Reduce 1 repetition and try again
                i--;
            }
        }
        return null;
    }

    private static String min_expand(MatchState ms, String s, String p, String ep) {
        while (true) {
            var res = match(ms, s, ep.substring(1));
            if (res != null) {
                return res;
            } else if (s.length() > 0 && singlematch(s.charAt(0), p, ep)) {
                // try with one more repetition
                s = s.substring(1);
            } else {
                return null;
            }
        }
    }

    private static int check_capture(MatchState ms, int l) {
        l = l - '1';
        if (l < 0 || l >= ms.level || ms.capture[l].len == CAP_UNFINISHED) {
            throw new LuaJavaError("error: invalid capture index");
        }
        return l;
    }

    private static int capture_to_close(MatchState ms) {
        for (int level = ms.level - 1; level >= 0; level--) {
            if (ms.capture[level].len == CAP_UNFINISHED)
                return level;
        }

        throw new InternalLuaRuntimeError("should be unreachable");
    }

    private static String classend(MatchState ms, String p) {
        char ch = p.charAt(0);
        p = p.substring(1);

        switch (ch) {
            case '%' -> {
                if (p.length() == 0) {
                    // error: malformed pattern, ends with '%'
                    throw new LuaJavaError("malformed pattern: ending with %");
                }
                return p.substring(1);
            }

            case '[' -> {
                if (p.charAt(0) == '^') {
                    p = p.substring(1);
                }
                // look for a ']'
                while (true) {
                    if (p.length() == 0) {
                        // error: malformed pattern (missing ']')
                        throw new LuaJavaError("malformed pattern: missing ]");
                    }

                    var pch = p.charAt(0);
                    p = p.substring(1);
                    if (pch == '%' && p.length() > 0) {
                        p = p.substring(1);
                    }

                    if (p.charAt(0) == ']')
                        break;
                }
                return p.substring(1);
            }

            default -> {
                return p;
            }
        }
    }

    private static String start_capture(MatchState ms, String s, String p, int what) {
        int level = ms.level;
        if (level >= LUA_MAXCAPTURES) {
            throw new LuaJavaError("Too many captures in pattern!");
        }

        ms.ensureCaptureIsSetUp(level);
        ms.capture[level].src = s;
        ms.capture[level].len = what;
        ms.level = level + 1;
        var res = match(ms, s, p);
        if (res == null) {
            ms.level--;
        }
        return res;
    }

    private static String end_capture(MatchState ms, String s, String p) {
        int l = capture_to_close(ms);
        if (l == -1) {
            return null;
        }

        ms.capture[l].len = ms.capture[l].src.length() - s.length();
        String res = match(ms, s, p);
        if (res == null) {
            ms.capture[l].len = CAP_UNFINISHED;
        }
        return res;
    }

    private static String match_capture(MatchState ms, String s, int l) {
        l = check_capture(ms, l);
        if (l == -1) return null;

        int clen = ms.capture[l].len;

        if (s.length() - clen >= 0 && ms.capture[l].src.substring(0, clen).equals(s.substring(0, clen)))
            return s.substring(clen);

        return null;
    }

    private static String match(MatchState ms, String s, String p) {
        final int JUMPDEST_INIT = 0;
        final int JUMPDEST_DFLT = 1;
        final int JUMPDEST_SKIPDFLT = 2;
        int jumpDest = 0;
        String ep = null;
        boolean m = false;
        while (true) {
            start:
            switch (jumpDest) {
                case JUMPDEST_INIT -> { // init
                    if (p.length() == 0)
                        return s;

                    // TODO not sure if this resetting here is correct, depends on how go handles jumping to before a declaration
//                    ep = null;
//                    m = false;

                    switch (p.charAt(0)) {
                        case '(' -> {
                            if (p.charAt(1) == ')') {
                                // TODO we dont support these
                                throw new UnsupportedOperationException("not implemented");
                            } else {
                                return start_capture(ms, s, p.substring(1), CAP_UNFINISHED);
                            }
                        }
                        case ')' -> {
                            return end_capture(ms, s, p.substring(1));
                        }
                        case '%' -> {
                            switch (p.charAt(1)) {
                                case 'b' -> {
                                    s = matchbalance(ms, s, p.substring(2));
                                    if (s == null)
                                        return null;
                                    p = p.substring(4);
                                }
                                // TODO support frontier pattern
                                default -> {
                                    if (Character.isDigit(p.charAt(1))) {
                                        s = match_capture(ms, s, (p.charAt(1)));
                                        if (s == null)
                                            return null;
                                        p = p.substring(2);

                                        jumpDest = JUMPDEST_INIT;
                                        break start;
                                    }
                                    jumpDest = JUMPDEST_DFLT;
                                    break start;
                                }
                            }
                        }
                        case '$' -> {
                            if (p.length() == 1) {
                                return s.length() == 0 ? s : null;
                            } else {
                                jumpDest = JUMPDEST_DFLT;
                                break start;
                            }
                        }
                        default -> {
                            jumpDest = JUMPDEST_DFLT;
                            break start;
                        }
                    }
                    jumpDest = JUMPDEST_SKIPDFLT;
                    break;
                }

                case JUMPDEST_DFLT -> {
                    ep = classend(ms, p);
                    m = s.length() > 0 && singlematch(s.charAt(0), p, ep);

                    // Handle the case where ep has run out so we can't index it
                    if (ep.length() == 0) {
                        if (!m) {
                            return null;
                        } else {
                            s = s.substring(1);
                            p = ep;
                            jumpDest = JUMPDEST_INIT;
                            break;
                        }
                    }

                    switch (ep.charAt(0)) {
                        case '?' -> {
                            // If s has run out, the optional match passes
                            if (s.length() == 0)
                                return "";

                            String res = match(ms, s.substring(1), ep.substring(1));
                            if (m && res != null) {
                                return res;
                            }
                            p = ep.substring(1);
                            jumpDest = JUMPDEST_INIT;
                            break start;
                        }

                        case '*' -> {
                            return max_expand(ms, s, p, ep);
                        }

                        case '+' -> {
                            return m ? max_expand(ms, s.substring(1), p, ep) : null;
                        }
                        case '-' -> {
                            return min_expand(ms, s, p, ep);
                        }
                        default -> {
                            if (!m) return null;
                            s = s.substring(1);
                            p = ep;
                            jumpDest = JUMPDEST_INIT;
                            break start;
                        }
                    }
                }

                case JUMPDEST_SKIPDFLT -> {
                    return null;
                }

                default -> {
                    throw new InvalidOpenTypeException("how did we get here?");
                }
            }
        }
    }

    private static class MatchState {

        public int level;
        public Capture[] capture;

        public void ensureCaptureIsSetUp(int level) {
            if (this.capture[level] == null) {
                this.capture[level] = new Capture();
            }
        }
    }

    private static class Capture {
        public String src;
        public int len;
    }

    // LUA BINDINGS
    public static LuaObject[] lua_match(String s, String pattern, int startIndex) {
        return lua_match_extended(s,pattern,startIndex).matchResult();
    }

    public record ExtendedMatchResult(LuaObject[] matchResult, FindResult res) {
    }

    public static ExtendedMatchResult lua_match_extended(String s, String pattern, int startIndex) {
        assert startIndex >= 0;
        FindResult resRaw = find(s.substring(startIndex), pattern, false);
        FindResult res = resRaw.adjustForStartIndex(startIndex);

        if (!res.success())
            return new ExtendedMatchResult(new LuaObject[]{LuaObject.NIL}, res);

        LuaObject[] rv;
        var caps = res.getCapturesOrEmpty();
        if (caps.length > 0) {
            rv = Arrays.stream(caps).map(LuaObject::of).toArray(LuaObject[]::new); // captures
        } else {
            rv = new LuaObject[]{LuaObject.of(s.substring(res.start(), res.end()))};
        }
        return new ExtendedMatchResult(rv, res);
    }
}
