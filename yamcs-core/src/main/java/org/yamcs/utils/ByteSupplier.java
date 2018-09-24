package org.yamcs.utils;

@FunctionalInterface
public interface ByteSupplier {
    /**
     * Gets a result.
     *
     * @return a result
     */
    byte getAsByte();
}
