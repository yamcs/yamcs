package org.yamcs.utils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation used to mark service that have been deprecated.
 * Information can be provided on how to resolve the problem (i.e. write the replacement service).
 *  
 * The information will be printed in the yamcs logs when the service is loaded.
 * 
 * @author nm
 *
 */
@Retention(value=RetentionPolicy.RUNTIME)
public @interface DeprecationInfo {
    String info();
}
