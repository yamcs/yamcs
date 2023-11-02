package org.yamcs.tests;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.client.mdb.MissionDatabaseClient;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.protobuf.Mdb.DataSourceType;

import com.google.common.io.Files;

public class MdbModificationPersistenceTest extends AbstractIntegrationTest {

    private MissionDatabaseClient mdbClient;

    @BeforeAll
    @AfterAll
    public static void copyEmptyXtce() throws IOException {
        Files.copy(new File("mdb/writable_subsys_empty.xml"), new File("mdb/writable_subsys.xml"));
    }

    @BeforeEach
    public void prepare() {
        mdbClient = yamcsClient.createMissionDatabaseClient(yamcsInstance);
    }

    @Test
    public void testCreate() throws Exception {
        mdbClient.createParameter("/writable_subsys/new_param1", DataSourceType.GROUND)
                .withParameterType("/REFMDB/uint32")
                .create()
                .get();

        // this will reload the MDB from file
        YConfiguration instanceConfig = YConfiguration.getConfiguration("yamcs." + yamcsInstance);
        var mdb = MdbFactory.createInstance(instanceConfig.getConfigList("mdb"), true, true);
        var p = mdb.getParameter("/writable_subsys/new_param1");
        assertNotNull(p);
    }
}
