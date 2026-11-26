package dev.asdf00.jluavm.internals;

public class BytecodeInterpreter {

    public static Object runFunc(Object[] constants, short[] bc, int bcIdx, int maxStackSize, int maxLocalCnt, Object[] args) {
        Object[] locals = new Object[maxLocalCnt];
        Object[] stack = new Object[maxStackSize];
        int sp = 0;
        while (true) {
            switch (bc[bcIdx] & 0xFF) {
                case 0 -> {
                    // example Add
                    if (true) {
                        Object a = stack[sp--], b = stack[sp--];
                        if (a instanceof Long al) {
                            if (b instanceof Long bl) {
                                stack[sp++] = al + bl;
                            } else if (b instanceof Double bd) {
                                stack[sp++] = al + bd;
                            } else {
                                // meta right
                            }
                        } else if (a instanceof Double ad) {
                            if (b instanceof Long bl) {
                                stack[sp++] = ad + bl;
                            } else if (b instanceof Double bd) {
                                stack[sp++] = ad + bd;
                            } else {
                                // meta right
                            }
                        } else {
                            // meta left or meta right
                        }
                        bcIdx++;
                    } else {
                        handleAdd(stack, sp);
                        // total stack change
                        sp -= 1;
                        // total movement
                        bcIdx++;
                    }
                }
                case 1 -> {
                    // example Call
                    // argument count is encoded in the upper bytecode byte
                    int argCnt = (bc[bcIdx] >>> 8) & 0xFF; // todo allow for more than 255 args
                    Object[] innerArgs = new Object[argCnt];
                    for (int i = argCnt - 1; i >= 0; i--) {
                        innerArgs[i] = stack[sp--];
                    }
                    Object func = stack[sp--];
                    // todo adjust innerArgs for func
                    // Object result = runFunc(func.constants, func.bytecode, 0, func.maxStackSize, func.maxLocalCnt, innerArgs)
                    Object result = null;
                    stack[sp++] = result;
                    bcIdx++;
                }
                case 3 -> {
                    // example jump
                    int target = (Integer) constants[bcIdx];
                    bcIdx = target;
                }
                // ...
            }
        }
    }

    private static void handleAdd(Object[] stack, int sp) {
        // example Add
        Object a = stack[sp--], b = stack[sp--];
        if (a instanceof Long al) {
            if (b instanceof Long bl) {
                stack[sp++] = al + bl;
            } else if (b instanceof Double bd) {
                stack[sp++] = al + bd;
            } else {
                // meta right
            }
        } else if (a instanceof Double ad) {
            if (b instanceof Long bl) {
                stack[sp++] = ad + bl;
            } else if (b instanceof Double bd) {
                stack[sp++] = ad + bd;
            } else {
                // meta right
            }
        } else {
            // meta left or meta right
        }
    }

}
