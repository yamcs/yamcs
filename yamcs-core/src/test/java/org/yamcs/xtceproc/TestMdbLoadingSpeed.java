package org.yamcs.xtceproc;

import org.junit.Ignore;
import org.junit.Test;
import org.yamcs.xtce.SpreadsheetLoader;

public class TestMdbLoadingSpeed {

    @Test
    @Ignore
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
