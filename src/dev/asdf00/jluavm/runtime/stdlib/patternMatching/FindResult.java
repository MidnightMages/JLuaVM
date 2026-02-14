package dev.asdf00.jluavm.runtime.stdlib.patternMatching;

public record FindResult(boolean success, int start, int end, String[] captures) {
    public String[] getCapturesOrEmpty() {
        return captures != null ? captures : new String[]{};
    }
}
