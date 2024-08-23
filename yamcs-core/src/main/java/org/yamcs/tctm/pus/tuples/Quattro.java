package org.yamcs.tctm.pus.tuples;

import java.util.Objects;

public class Quattro<K, V, L, M> {
    private final K first;
    private final V second;
    private final L third;
    private final M fourth;

    public Quattro(K first, V second, L third, M fourth) {
        this.first = first;
        this.second = second;
        this.third = third;
        this.fourth = fourth;
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

    public M getFourth() {
        return fourth;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Quattro<?, ?, ?, ?> quattro = (Quattro<?, ?, ?, ?>) o;

        return Objects.equals(first, quattro.first) && Objects.equals(second, quattro.second) && Objects.equals(third, quattro.third) && Objects.equals(fourth, quattro.fourth);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second, third, fourth);
    }
}
