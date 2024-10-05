package dev.asdf00.jluavm.parsing.container;

public record Position(int line, int col, int sourcePt) {
    public String sourcePos() {
        return "(line %s, col %s)".formatted(line, col);
    }
}
