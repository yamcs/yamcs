package org.yamcs.yarch.rocksdb;

import java.util.Map;

import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.Tag;

import static org.yamcs.yarch.rocksdb.TablespaceRepresenter.*;


public class TablespaceConstructor  extends Constructor {
    private String tablespaceName;
    public TablespaceConstructor(String tablespaceName) {
        this.yamlConstructors.put(new Tag("Tablespace"), new ConstructTablespace());
        this.tablespaceName = tablespaceName;
    }
    private class ConstructTablespace extends AbstractConstruct {
        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Override
        public Object construct(Node node) {
            Map<String, Object> m = (Map) constructMapping((MappingNode)node);
            int id;
            if(!m.containsKey(K_ID)) {
                throw new IllegalArgumentException("No id specified");
            }
            id = (Integer)m.get(K_ID);
            Tablespace tblspc = new Tablespace(tablespaceName, (byte)id);
            if(m.containsKey(K_DATA_DIR)) {
                tblspc.setCustomDataDir((String)m.get(K_DATA_DIR));
            }
            return tblspc;
        }
    }
}
