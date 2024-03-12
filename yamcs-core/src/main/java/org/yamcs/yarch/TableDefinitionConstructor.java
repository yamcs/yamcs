package org.yamcs.yarch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.yamcs.yarch.PartitioningSpec._type;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

/**
 * Constructs {@link org.yamcs.yarch.TableDefinition} from .def yaml files.
 * 
 *
 */
public class TableDefinitionConstructor extends Constructor {
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

    public TableDefinitionConstructor() {
        super(new LoaderOptions());
        this.yamlConstructors.put(new Tag("TableDefinition"), new ConstructTableDefinition());
        this.yamlConstructors.put(new Tag("TupleDefinition"), new ConstructTupleDefinition());
        this.yamlConstructors.put(new Tag("PartitioningSpec"), new ConstructPartitioningSpec());
    }

    private class ConstructTableDefinition extends AbstractConstruct {
        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Override
        public Object construct(Node node) {
            Map<String, Object> m = (Map) constructMapping((MappingNode) node);
            TupleDefinition tkeyDef = (TupleDefinition) m.get(K_KEY_DEF);
            TupleDefinition tvalueDef = (TupleDefinition) m.get(K_VALUE_DEF);

            Map<String, BiMap<String, Short>> enumValues = new HashMap<>();
            if (m.containsKey(K_ENUM_VALUE)) {
                Map<String, Map<String, Integer>> t = (Map) m.get(K_ENUM_VALUE);
                for (Entry<String, Map<String, Integer>> e : t.entrySet()) {
                    BiMap<String, Short> b = HashBiMap.create();
                    for (Entry<String, Integer> e1 : e.getValue().entrySet()) {
                        b.put(e1.getKey(), (short) (int) e1.getValue());
                    }
                    enumValues.put(e.getKey(), b);
                }
            }
            int formatVersion = 0;
            if (m.containsKey(K_FORMAT_VERSION)) {
                formatVersion = (Integer) m.get(K_FORMAT_VERSION);
            }
            List<TableColumnDefinition> keyDef = new ArrayList<>();
            for (ColumnDefinition cd : tkeyDef.getColumnDefinitions()) {
                TableColumnDefinition tcd = new TableColumnDefinition(cd);
                tcd.setEnumValues(enumValues.get(cd.getName()));
                keyDef.add(tcd);
            }

            List<TableColumnDefinition> valueDef = new ArrayList<>();
            for (ColumnDefinition cd : tvalueDef.getColumnDefinitions()) {
                TableColumnDefinition tcd = new TableColumnDefinition(cd);
                tcd.setEnumValues(enumValues.get(cd.getName()));
                valueDef.add(tcd);
            }

            TableDefinition tdef = new TableDefinition(formatVersion, keyDef, valueDef);
            if (m.containsKey(K_HISTOGRAM)) {
                List<String> h = (List<String>) m.get(K_HISTOGRAM);
                try {
                    tdef.setHistogramColumns(h);
                } catch (StreamSqlException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            try {
                if (m.containsKey(K_PARTITIONING_SPEC)) {
                    tdef.setPartitioningSpec((PartitioningSpec) m.get(K_PARTITIONING_SPEC));
                } else {
                    PartitioningSpec ps = PartitioningSpec.noneSpec();
                    tdef.setPartitioningSpec(ps);
                }
            } catch (StreamSqlException e) {
                throw new IllegalArgumentException(e);
            }
            if (m.containsKey(K_COMPRESSED)) {
                tdef.setCompressed((Boolean) m.get(K_COMPRESSED));
            }
            if (m.containsKey(K_STORAGE_ENGINE)) {
                tdef.setStorageEngineName((String) m.get(K_STORAGE_ENGINE));
            } else {// before the storageEngine has been invented, we only had TokyoCabinet, so assume that if it's not
                    // set then TokyoCabine is used
                tdef.setStorageEngineName("TokyoCabinet");
            }

            return tdef;
        }
    }

    private class ConstructTupleDefinition extends AbstractConstruct {
        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Override
        public Object construct(Node node) {
            List<Object> l = (List) constructSequence((SequenceNode) node);

            ArrayList<ColumnDefinition> cols = new ArrayList<>();
            for (Object o : l) {
                Map<String, Object> m = (Map) o;
                Object o1 = m.get("idx");
                if (!(o1 instanceof Integer)) {
                    throw new IllegalArgumentException("idx not specified or not integer");
                }
                int idx = (Integer) o1;
                if (idx > TupleDefinition.MAX_COLS) {
                    throw new IllegalArgumentException("got idx=" + idx + " but max_cols=" + TupleDefinition.MAX_COLS);
                }
                String name = (String) m.get("name");
                if (name == null) {
                    throw new IllegalArgumentException("name not specified for column with index idx=" + idx);
                }

                DataType type;
                // Old events.def files may have have a reference to this. It is the API-level message
                // before existence of Db.Event, but its package changed since Yamcs 5.6.x
                if ("PROTOBUF(org.yamcs.protobuf.Yamcs$Event)".equals(m.get("type"))) {
                    type = DataType.byName("PROTOBUF(" + org.yamcs.protobuf.Event.class.getName() + ")");
                } else {
                    type = DataType.byName((String) m.get("type"));
                }

                ColumnDefinition cd = new ColumnDefinition(name, type);

                for (int i = cols.size(); i < idx + 1; i++) {
                    cols.add(null);
                }
                cols.set(idx, cd);
            }
            TupleDefinition td = new TupleDefinition();
            for (int i = 0; i < cols.size(); i++) {
                ColumnDefinition cd = cols.get(i);
                if (cd == null) {
                    throw new IllegalArgumentException("Column with idx " + i + " not specified");
                }
                td.addColumn(cd);
            }
            return td;
        }
    }

    private class ConstructPartitioningSpec extends AbstractConstruct {
        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Override
        public Object construct(Node node) {
            Map<String, Object> m = (Map) constructMapping((MappingNode) node);
            if (!m.containsKey("type")) {
                throw new IllegalArgumentException("partitioning spec type not specified");
            }

            PartitioningSpec pspec;
            PartitioningSpec._type type = PartitioningSpec._type.valueOf((String) m.get("type"));
            if (type == _type.NONE) {
                pspec = PartitioningSpec.noneSpec();
            } else if (type == _type.TIME) {
                String timeColumn = (String) m.get(K_TIME_COLUMN);
                pspec = PartitioningSpec.timeSpec(timeColumn, getSchema(m));
            } else if ((type == _type.VALUE)) {
                String valueColumn = (String) m.get(K_VALUE_COLUMN);
                pspec = PartitioningSpec.valueSpec(valueColumn);
            } else if (type == _type.TIME_AND_VALUE) {
                String timeColumn = (String) m.get(K_TIME_COLUMN);
                String valueColumn = (String) m.get(K_VALUE_COLUMN);
                pspec = PartitioningSpec.timeAndValueSpec(timeColumn, valueColumn, getSchema(m));
            } else {
                throw new IllegalArgumentException("Unknown partitioning type " + type);
            }


            return pspec;
        }
    }

    private String getSchema(Map<String, Object> m) {
        if (m.containsKey(K_TIME_PARTITIONING_SCHEMA)) {
            return (String) m.get(K_TIME_PARTITIONING_SCHEMA);
        } else {// this probably should indicate some sort of database corruption but we keep it for compatibility
                // reasons
            return "YYYY/DOY";
        }
    }

}
