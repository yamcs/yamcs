package org.yamcs.yarch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.yarch.PartitioningSpec._type;
import org.yamcs.yarch.TableDefinition.PartitionStorage;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Serializes TableDefinition to yaml format
 * @author nm
 *
 */
public class TableDefinitionRepresenter extends Representer {
    public static final String K_COMPRESSED = "compressed";
    public static final String K_KEY_DEF = "keyDef";
    public static final String K_VALUE_DEF = "valueDef";
    public static final String K_DATA_DIR = "dataDir";
    public static final String K_HISTOGRAM = "histogram";
    public static final String K_ENUM_VALUE = "enumValues";
    public static final String K_PARTITIONING_SPEC = "partitioningSpec";	
    public static final String K_TIME_COLUMN = "timeColumn";
    public static final String K_VALUE_COLUMN = "valueColumn";
    public static final String K_TIME_PARTITIONING_SCHEMA = "timePartitioningSchema";
    public static final String K_STORAGE_ENGINE = "storageEngine";
    public static final String K_PARTITION_STORAGE = "partitionStorage";
    public static final String K_FORMAT_VERSION = "formatVersion";


    public TableDefinitionRepresenter() {
        this.representers.put(TableDefinition.class, new RepresentTableDefinition());
        this.representers.put(TupleDefinition.class, new RepresentTupleDefinition());
        this.representers.put(PartitioningSpec.class, new RepresentPartitioningSpec());
        this.representers.put(PartitionStorage.class, new RepresentPartitioningStorage());
    }

    private class RepresentTableDefinition implements Represent {
        @Override
        public Node representData(Object data) {
            TableDefinition td = (TableDefinition) data;
            Map<String, Object> m=new HashMap<>();
            m.put(K_COMPRESSED, td.isCompressed());
            m.put(K_KEY_DEF, td.getKeyDefinition());
            m.put(K_VALUE_DEF, td.serializedValueDef);
            m.put(K_STORAGE_ENGINE, td.getStorageEngineName());
            m.put(K_PARTITION_STORAGE, td.getPartitionStorage());
            m.put(K_FORMAT_VERSION, td.getFormatVersion());

            if(td.hasHistogram()) {
                m.put(K_HISTOGRAM, td.getHistogramColumns());
            }
            if(td.serializedEmumValues!=null) {
                m.put(K_ENUM_VALUE, td.serializedEmumValues);
            }
            if(td.hasCustomDataDir()) {
                m.put(K_DATA_DIR, td.getDataDir());
            }
            if(td.hasPartitioning()) {
                m.put(K_PARTITIONING_SPEC, td.getPartitioningSpec());
            }
            return representMapping(new Tag("TableDefinition"), m, false);
        }
    }

    private class RepresentTupleDefinition implements Represent {
        @Override
        public Node representData(Object data) {
            TupleDefinition td = (TupleDefinition) data;
            List<Object> list=new ArrayList<>(td.size());
            for(int i=0;i<td.size();i++) {
                ColumnDefinition cd=td.getColumn(i);
                Map<String, Object> m=new HashMap<>();
                m.put("idx", i);
                m.put("name", cd.getName());
                m.put("type", cd.getType().toString());
                list.add(m);
            }
            return representSequence(new Tag("TupleDefinition"), list, false);
        }
    }

    private class RepresentPartitioningSpec implements Represent {
        @Override
        public Node representData(Object data) {
            PartitioningSpec p = (PartitioningSpec) data;
            Map<String, Object> m=new HashMap<>();
            m.put("type", p.type.toString());
            if((p.type==_type.TIME) || (p.type==_type.TIME_AND_VALUE)) {
                m.put("timeColumn", p.timeColumn);
                m.put(K_TIME_PARTITIONING_SCHEMA, p.getTimePartitioningSchema().getName());
            }
            if((p.type==_type.VALUE) || (p.type==_type.TIME_AND_VALUE)) {
                m.put(K_VALUE_COLUMN, p.valueColumn);
            }


            return representMapping(new Tag("PartitioningSpec"), m, true);
        }
    }

    private class RepresentPartitioningStorage implements Represent {
        @Override
        public Node representData(Object data) {
            PartitionStorage p = (PartitionStorage) data;

            return representScalar(new Tag("PartitionStorage"), p.name());
        }
    }
}
