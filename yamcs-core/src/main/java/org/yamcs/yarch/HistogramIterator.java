package org.yamcs.yarch;

import java.util.Iterator;

public interface HistogramIterator extends AutoCloseable, Iterator<HistogramRecord> {
    void close();
}
