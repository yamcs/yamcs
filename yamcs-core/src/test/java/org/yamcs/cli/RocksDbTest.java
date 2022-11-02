package org.yamcs.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.rocksdb.RocksDB;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.FileUtils;

public class RocksDbTest extends AbstractCliTest {

    @Test
    public void testRocksdbCompaction() throws Exception {
        Path etcdata = createTmpEtcData();
        try {
            int n = 10_000;
            YamcsAdminCli yamcsCli = new YamcsAdminCli();
            String rdbDir = etcdata + "/yamcs-data";
            try (RocksDB db = RocksDB.open(rdbDir)) {
                byte[] key = new byte[10];
                byte[] value = new byte[1024];
                Random r = new Random();
                for (int i = 0; i < n; i++) {
                    ByteArrayUtils.encodeInt(n - i, key, 0);
                    r.nextBytes(value);
                    db.put(key, value);
                }
            }

            yamcsCli.parse(new String[] { "rocksdb", "compact", "--dbDir", rdbDir, "--sizeMB", "1" });
            yamcsCli.validate();
            yamcsCli.execute();

            boolean allSstFilesSmallerThan1Mb = Files.list(etcdata.resolve("yamcs-data"))
                    .filter(p -> p.toString().endsWith(".sst"))
                    .allMatch(p -> {
                        try {// allow a bit of margin 1.3MB instead of 1MB
                            return Files.size(p) < 1.3 * 1024 * 1024;
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
            assertTrue(allSstFilesSmallerThan1Mb);

        } finally {
            FileUtils.deleteRecursively(etcdata);
        }
    }

    @Test
    public void testRocksdbHelp() throws Exception {
        assertEquals(0, runMain("rocksdb", "--help"));
        assertTrue(mconsole.output().contains("bench      Benchmark rocksdb storage engine"));
    }
}
