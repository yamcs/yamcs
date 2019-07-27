package org.yamcs;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class or method as experimental. This means that it may be changed or removed without needing a deprecation
 * phase in the future.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ ElementType.TYPE, ElementType.METHOD })
@Documented
public @interface Experimental {

    /**
     * Optional information on this feature.
     */
    String value() default "";
}
