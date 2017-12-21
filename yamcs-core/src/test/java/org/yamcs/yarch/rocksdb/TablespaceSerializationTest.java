package org.yamcs.yarch.rocksdb;

import org.junit.Test;
import org.yaml.snakeyaml.Yaml;
import static org.junit.Assert.assertEquals;

public class TablespaceSerializationTest {
    
    @Test
    public void test1() {
        Yaml yaml=new Yaml(new TablespaceConstructor("aaa"), new TablespaceRepresenter());
        Tablespace t1 = new Tablespace("aaa", (byte)1);
        t1.setCustomDataDir("/tmp/abc");
        Tablespace t2 = (Tablespace)yaml.load(yaml.dump(t1));
        assertEquals("/tmp/abc", t2.getCustomDataDir());
        assertEquals(1, t2.getId());
        
    }
}
