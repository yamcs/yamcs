package org.yamcs.yarch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.yamcs.yarch.PartitioningSpec._type;
import org.yamcs.yarch.TableDefinition.PartitionStorage;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import static org.yamcs.yarch.TableDefinitionRepresenter.*;

/**
 * Constructs {@link org.yamcs.yarch.TableDefinition} from .def yaml files.
 * 
 *
 */
public class TableDefinitionConstructor  extends Constructor {
    public TableDefinitionConstructor() {
        this.yamlConstructors.put(new Tag("TableDefinition"), new ConstructTableDefinition());
        this.yamlConstructors.put(new Tag("TupleDefinition"), new ConstructTupleDefinition());
        this.yamlConstructors.put(new Tag("PartitioningSpec"), new ConstructPartitioningSpec());
        this.yamlConstructors.put(new Tag("PartitionStorage"), new ConstructPartitionStorage());
    }

    private class ConstructTableDefinition extends AbstractConstruct {
        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Override
        public Object construct(Node node) {
            Map<String, Object> m = (Map) constructMapping((MappingNode)node);
            TupleDefinition keyDef=(TupleDefinition) m.get(K_KEY_DEF);
            TupleDefinition valueDef=(TupleDefinition) m.get(K_VALUE_DEF);
            Map<String, BiMap<String,Short>> enumValues=new HashMap<>();
            if(m.containsKey(K_ENUM_VALUE)) {
                Map<String, Map<String, Integer>> t=(Map)m.get(K_ENUM_VALUE);
                for(Entry<String, Map<String, Integer>> e:t.entrySet()) {
                    BiMap<String, Short> b=HashBiMap.create();
                    for(Entry<String,Integer> e1:e.getValue().entrySet()) {
                        b.put(e1.getKey(), (short)(int)e1.getValue());
                    }
                    enumValues.put(e.getKey(), b);
                }
            }
            TableDefinition tdef=new TableDefinition(keyDef, valueDef, enumValues);
            if(m.containsKey(K_HISTOGRAM)) {
                List<String> h=(List<String>)m.get(K_HISTOGRAM);
                try {
                    tdef.setHistogramColumns(h);
                } catch (StreamSqlException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            if(m.containsKey(K_DATA_DIR)) {
                tdef.setCustomDataDir(true);
                tdef.setDataDir((String)m.get(K_DATA_DIR));
            }
            try {
                if(m.containsKey(K_PARTITIONING_SPEC)) {
                    tdef.setPartitioningSpec((PartitioningSpec)m.get(K_PARTITIONING_SPEC));
                } else {
                    PartitioningSpec ps = PartitioningSpec.noneSpec();
                    tdef.setPartitioningSpec(ps);
                }
            } catch (StreamSqlException e) {
                throw new IllegalArgumentException(e);
            }
            if(m.containsKey(K_COMPRESSED)) {
                tdef.setCompressed((Boolean)m.get(K_COMPRESSED));
            }
            
            if(m.containsKey(K_FORMAT_VERSION)) {
                tdef.setFormatVersion((Integer)m.get(K_FORMAT_VERSION));
            } else {
                tdef.setFormatVersion(0);
            }
            
            if(m.containsKey(K_STORAGE_ENGINE)) {
                tdef.setStorageEngineName((String)m.get(K_STORAGE_ENGINE));
            } else {//before the storageEngine has been invented, we only had TokyoCabinet, so assume that if it's not set then TokyoCabine is used
                tdef.setStorageEngineName("TokyoCabinet");
            }
            
            if(m.containsKey(K_PARTITION_STORAGE)) {
                tdef.setPartitionStorage((PartitionStorage)m.get(K_PARTITION_STORAGE));
            } else {//before the partitionStorage has been invented, we only had column_family
                tdef.setPartitionStorage(PartitionStorage.COLUMN_FAMILY);
            }

            return tdef;
        }
    }

    private class ConstructTupleDefinition extends AbstractConstruct {
        @SuppressWarnings({"unchecked","rawtypes"})
        @Override
        public Object construct(Node node) {
            List<Object> l = (List) constructSequence((SequenceNode)node);

            ArrayList<ColumnDefinition> cols=new ArrayList<ColumnDefinition>();
            for(Object o:l) {
                Map<String, Object> m=(Map)o;
                Object o1=m.get("idx");
                if((o1==null) || !(o1 instanceof Integer)){
                    throw new IllegalArgumentException("idx not specified or not integer");
                }
                int idx=(Integer)o1;
                if(idx>TupleDefinition.MAX_COLS){
                    throw new IllegalArgumentException("got idx="+idx+" but max_cols="+TupleDefinition.MAX_COLS);
                }
                String name=(String)m.get("name");
                if(name==null) {
                    throw new IllegalArgumentException("name not specifie for column with index idx="+idx);
                }
                DataType type = DataType.byName((String)m.get("type"));

                ColumnDefinition cd = new ColumnDefinition(name, type);

                for(int i=cols.size();i<idx+1;i++) {
                    cols.add(null);
                }
                cols.set(idx, cd);
            }
            TupleDefinition td=new TupleDefinition();
            for(int i=0;i<cols.size();i++) {
                ColumnDefinition cd=cols.get(i);
                if(cd==null) {
                    throw new IllegalArgumentException("Column with idx "+i+" not specified");
                }
                td.addColumn(cd);
            }
            return td;
        }
    }

    private class ConstructPartitioningSpec extends AbstractConstruct {
        @SuppressWarnings({"unchecked","rawtypes"})
        @Override
        public Object construct(Node node) {
            Map<String, Object> m = (Map) constructMapping((MappingNode)node);
            if(!m.containsKey("type")){
                throw new IllegalArgumentException("partitioning spec type not specified");
            }

            PartitioningSpec pspec;
            PartitioningSpec._type type = PartitioningSpec._type.valueOf((String)m.get("type"));
            if(type==_type.NONE) {
                pspec = PartitioningSpec.noneSpec();
            } else if(type==_type.TIME) {
                String timeColumn = (String)m.get(K_TIME_COLUMN);
                pspec = PartitioningSpec.timeSpec(timeColumn);
            } else if((type==_type.VALUE)) {
                String valueColumn = (String)m.get(K_VALUE_COLUMN);
                pspec = PartitioningSpec.valueSpec(valueColumn);
            } else if(type==_type.TIME_AND_VALUE) {
                String timeColumn = (String)m.get(K_TIME_COLUMN);
                String valueColumn = (String)m.get(K_VALUE_COLUMN);
                pspec = PartitioningSpec.timeAndValueSpec(timeColumn, valueColumn);				
            } else {
                throw new IllegalArgumentException("Unkwnon partitioning type "+type);
            }


            if(m.containsKey(K_TIME_PARTITIONING_SCHEMA)) {
                pspec.setTimePartitioningSchema((String)m.get(K_TIME_PARTITIONING_SCHEMA));
            } else {
                pspec.setTimePartitioningSchema("YYYY/DOY");
            }
            return pspec;
        }
    }
    
    private class ConstructPartitionStorage extends AbstractConstruct {
        @Override
        public Object construct(Node node) {
            String ps = (String) constructScalar((ScalarNode)node);
            return PartitionStorage.valueOf(ps.toUpperCase());
        }
    }
}