package org.yamcs.utils;

/**
 * An interface for an iterator that allows peeking at the current value.
 * <p>
 * The advantage over the standard java iterator is that because the value can be looked at, it can be used in priority
 * queues to run multiple of them in parallel.
 * 
 * @param <T>
 *            the type of elements returned by this iterator
 */
public interface PeekingIterator<T> {

    /**
     * Returns {@code true} if the iterator has more elements.
     * <p>
     * This method allows to verify whether the iterator has a valid value to be fetched.
     *
     * @return {@code true} if the iterator has more elements or {@code false} otherwise
     */
    boolean isValid();

    /**
     * Returns the current value from the iterator without advancing.
     * <p>
     * This method can only be called if {@link #isValid()} returns {@code true}. If called after {@link #isValid()}
     * returns {@code false}, this method will throw an exception.
     *
     * @return the current element
     * @throws IllegalStateException
     *             if {@link #isValid()} returns {@code false}
     */
    T value();

    /**
     * Moves the iterator to the next element.
     * <p>
     * If {@link #isValid()} returns {@code false}, calling this method has no effect.
     */
    void next();
}
