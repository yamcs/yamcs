package org.yamcs.parameterarchive;

import java.io.PrintStream;

import org.junit.Ignore;
import org.junit.Test;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class PrintStats {
    @Ignore
    @Test
    public void test1() throws Exception {
        TimeEncoding.setUp();
        YarchDatabase.setHome("/storage/yamcs-data");
        
        ParameterArchiveV2 parchive = new ParameterArchiveV2("aces-ops");
        PrintStream ps = new PrintStream("/tmp/aces-ops6-stats.txt");
        PrintStream ps1 = new PrintStream("/storage/aces-ops-stats/aces-ops6-paraid.txt");
        parchive.printKeys(ps);
        parchive.getParameterIdDb().print(ps1);
        ps1.close();
        ps.close();
    }
}
