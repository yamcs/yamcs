package org.yamcs.yarch.rocksdb;

import java.util.HashMap;
import java.util.Map;

import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Serializes {@link Tablespace} to yaml format
 * @author nm
 *
 */
public class TablespaceRepresenter extends Representer {
    public static final String K_DATA_DIR = "dataDir";
    public static final String K_ID = "id";	


    public TablespaceRepresenter() {
        this.representers.put(Tablespace.class, new RepresentTablespace());
    }

    private class RepresentTablespace implements Represent {
        @Override
        public Node representData(Object data) {
            Tablespace tblsp = (Tablespace) data;
            Map<String, Object> m=new HashMap<>();
            if(tblsp.getCustomDataDir()!=null) {
                m.put(K_DATA_DIR, tblsp.getCustomDataDir());
            }
            m.put(K_ID, tblsp.getId());
            return representMapping(new Tag("Tablespace"), m, false);
        }
    }
}
