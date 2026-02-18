package dev.asdf00.jluavm.runtime.stdlib.patternMatching;

public record FindResult(boolean success, int start, int end, String[] captures) {
    public String[] getCapturesOrEmpty() {
        return captures != null ? captures : new String[]{};
    }

    public FindResult adjustForStartIndex(int startIndex) {
        return new FindResult(success, start+startIndex, end+startIndex, captures);
    }
}
