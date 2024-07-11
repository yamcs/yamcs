package org.yamcs.tctm.pus.tuples;

import java.util.Objects;

public class Triple<K, V, L> {
    private final K first;
    private final V second;
    private final L third;

    public Triple(K first, V second, L third) {
        this.first = first;
        this.second = second;
        this.third = third;
    }

    public K getFirst() {
        return first;
    }

    public V getSecond() {
        return second;
    }

    public L getThird() {
        return third;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Triple<?, ?, ?> triple = (Triple<?, ?, ?>) o;

        return Objects.equals(first, triple.first) && Objects.equals(second, triple.second) && Objects.equals(third, triple.third);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second, third);
    }
}