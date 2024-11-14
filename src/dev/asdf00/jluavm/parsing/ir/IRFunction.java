package dev.asdf00.jluavm.parsing.ir;

public class IRFunction extends IRBlock {
    public final String jClassName;
    public boolean hasParams;

    public IRFunction(String jClassName) {
        super(null, 0);
        this.jClassName = jClassName;
        this.hasParams = false;
    }

    public String toJavaClass() {
        return null;
    }

    public String[] getInnerFunctions() {
        return null;
    }
}
