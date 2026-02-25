package dev.asdf00.jluavm.utils;

@FunctionalInterface
public interface QuadFunction<P0, P1, P2, P3, R> {
    R apply(P0 p0, P1 p1, P2 p2, P3 p3);
}
