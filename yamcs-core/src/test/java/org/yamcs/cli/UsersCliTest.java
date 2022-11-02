package org.yamcs.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.rocksdb.RocksDB;
import org.yamcs.security.Directory;
import org.yamcs.security.User;
import org.yamcs.utils.FileUtils;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;

public class UsersCliTest extends AbstractCliTest {
    static Path etcdata;

    @BeforeAll
    public static void createDb() throws IOException {
        RocksDB.loadLibrary();
        etcdata = createTmpEtcData();
    }

    @AfterAll
    public static void cleanup() throws IOException {
        RdbStorageEngine.getInstance().shutdown();
        FileUtils.deleteRecursively(etcdata);
    }

    @Test
    public void test1() throws Exception {
        YamcsAdminCli yamcsCli = new YamcsAdminCli();

        Directory directory = new Directory();
        User user = new User("test1", null);
        user.setDisplayName("Mr Test1");
        directory.addUser(user);

        yamcsCli.parse(new String[] { "users", "list" });
        yamcsCli.validate();
        yamcsCli.execute();

        String out = mconsole.output();
        assertTrue(out.contains("test1     Mr Test1"));

        mconsole.reset();
        yamcsCli.parse(new String[] { "users", "describe", "test1" });
        yamcsCli.validate();
        yamcsCli.execute();
        out = mconsole.output();

        assertTrue(out.contains("username:      test1"));
        assertTrue(out.contains("display name:  Mr Test1"));

        char[] password = "test1-pass".toCharArray();
        mconsole.reset();
        mconsole.setPassword(password, password);
        yamcsCli.parse(new String[] { "users", "reset-password", "test1" });
        yamcsCli.validate();
        yamcsCli.execute();

        // make a new directory to read the users from the rocksdb database
        directory = new Directory();
        assertTrue(directory.validateUserPassword("test1", password));
    }

    @Test
    public void testInvalidUser() {
        assertEquals(-1, runMain("--etc-dir", etcdata.toString(), "users", "describe", "test2"));
        assertTrue(mconsole.output().contains("invalid user '[test2]'"));
    }
}
