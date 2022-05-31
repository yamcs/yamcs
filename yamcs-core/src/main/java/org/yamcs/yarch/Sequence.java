package org.yamcs.yarch;

/**
 * Sequences generate incrementing numbers and are persisted to the database (i.e. upon restart they continue from where
 * they left)
 * 
 * 
 * @author nm
 *
 */
public interface Sequence {
    long get();
    long next() throws YarchException;
    void reset(long value);
}
