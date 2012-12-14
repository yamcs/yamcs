package org.yamcs;

/**
 * @author nm
 * Annotation recommended by the book "Java Concurrency in Practice"
 */
public @interface GuardedBy {
	String value();
}
