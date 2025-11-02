package dev.asdf00.jluavm.api.userdata;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LuaExposed {
    enum Policy {
        READ, WRITE, READWRITE
    }

    Policy value() default Policy.READWRITE;
}
