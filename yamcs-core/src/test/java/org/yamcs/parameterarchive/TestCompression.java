package org.yamcs.parameterarchive;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.ByteBuffer;

import org.junit.Ignore;
import org.junit.Test;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.CompressionType;
import org.rocksdb.FlushOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.yamcs.utils.FileUtils;

public class TestCompression {

    @Ignore
    @Test
    public void test() throws Exception {
        String path = "/tmp/testcompression";
        FileUtils.deleteRecursively(new File(path).toPath());

        Options options = new Options();
        options.setCreateIfMissing(true);
        //options.setCompressionType(CompressionType.SNAPPY_COMPRESSION);
        options.setCompressionType(CompressionType.SNAPPY_COMPRESSION);

        ColumnFamilyOptions cfo = new ColumnFamilyOptions();
        cfo.setCompressionType(CompressionType.SNAPPY_COMPRESSION);
        //
        RocksDB rdb = RocksDB.open(options, path);
        int numparam = 1;
        int pphour = 3600;
        int nhours = 10000;
     //   WriteOptions wo = new WriteOptions();
      //  wo.setSync(false);
        for(int j=0; j<numparam; j++) {
            String s = "parameter_"+j;
            ColumnFamilyDescriptor cfd = new ColumnFamilyDescriptor(s.getBytes(), cfo);
            ColumnFamilyHandle cfh = rdb.createColumnFamily(cfd);
            
            for(int i=0;i<nhours;i++) {
                byte[] key = new byte[8];
                ByteBuffer.wrap(key).putLong(i*1000L);

                byte[] value = getIncreasingIntValue(pphour);
                rdb.put(cfh,key, value);
            }
        }
        FlushOptions flushOptions = new FlushOptions();
        rdb.flush(flushOptions);
        System.out.println("stats: "+rdb.getProperty("rocksdb.stats"));
        
        rdb.close();
    }


    byte[] getIncreasingIntValue(int n) {
        int keySize = 2+4;
        byte[] v = new byte[n*keySize];
        int m = 100000;
        ByteBuffer bb = ByteBuffer.wrap(v);
        for(int i=0;i<n; i++) {
           // bb.putShort(2*i, (short)0);
           bb.putShort(2*i, (short)i);
        }
        for(int i=0;i<n; i++) {
            //bb.putInt(2*n+4*i, m+i);
           
            bb.putInt(2*n+4*i, m);
        }
        return v;
    }

}
