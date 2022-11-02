package org.yamcs.mdb;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class TestMdbLoadingSpeed {

    @Test
    @Disabled
    public void test1() {
        for (int i = 0; i < 100; i++) {
            long t0 = System.currentTimeMillis();
            SpreadsheetLoader sl = new SpreadsheetLoader("/home/nm/git/scs/mdb/arbitrary-binary.xls");
            sl.load();

            long t1 = System.currentTimeMillis();

            System.out.println("took " + (t1 - t0) + " ms");
        }
    }
}
