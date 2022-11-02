package org.yamcs.parameterarchive;

import java.io.PrintStream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.YarchDatabase;

public class PrintStats {

    @Test
    @Disabled
    public void test1() throws Exception {
        TimeEncoding.setUp();
        YarchDatabase.setHome("/storage/yamcs-data");

        ParameterArchive parchive = new ParameterArchive();
        YConfiguration config = parchive.getSpec().validate(YConfiguration.emptyConfig());
        parchive.init("aces-ops", "test", config);
        PrintStream ps = new PrintStream("/tmp/aces-ops6-stats.txt");
        PrintStream ps1 = new PrintStream("/storage/aces-ops-stats/aces-ops6-paraid.txt");
        parchive.printKeys(ps);
        parchive.getParameterIdDb().print(ps1);
        ps1.close();
        ps.close();
    }
}
