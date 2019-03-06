package org.yamcs.cfdp;

import java.util.concurrent.atomic.AtomicInteger;

final class IdGenerator {
    private final AtomicInteger sequence = new AtomicInteger(1);

    public int generate() {
        return sequence.getAndIncrement();
    }

}
