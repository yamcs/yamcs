package org.yamcs.http.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Routes.class)
public @interface Route {

    /**
     * Currently must be an absolute path. Specify route params by preceding them with a colon, followed by their
     * identifying name.
     */
    String path();

    /**
     * HTTP method or methods by which this rule is available. By default set to "GET".
     */
    String[] method() default { "GET" };

    /**
     * Data load routes expect to receive a large body and they receive it piece by piece in HttpContent objects.
     * 
     * See {@link ArchiveTableRestHandler } for an example on how to implement this.
     * 
     * For the normal routes (where dataLoad=false) the body is limited to {@link #maxBodySize()} bytes provided to the
     * HttpObjectAgregator.
     */
    boolean dataLoad() default false;

    /**
     * Set true if the execution of the route is expected to take a long time (more than 0.5 seconds). It will be
     * executed on another thread.
     * 
     * Leave false if the execution uses its own off thread mechanism (most of the routes should do that).
     * 
     * @return
     */
    boolean offThread() default false;

    int maxBodySize() default Router.MAX_BODY_SIZE;
}
