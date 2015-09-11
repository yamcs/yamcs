package org.yamcs.yarch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.yamcs.yarch.PartitioningSpec._type;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import static org.yamcs.yarch.TableDefinitionRepresenter.*;

/**
 * Constructs {@link org.yamcs.yarch.TableDefinition} from .def yaml files.
 * 
 * Note on the storage engine: before we had multiple storage engines, the only one existing was TokyoCabinets. 
 * So, when the .def file do not specify the storage engine, we assume it's TokyoCabinets. 
 * That despite the fact that when creating tables with the create table statement, a different storage engine is assumed as default.
 * 
 * @author nm
 *
 */
public class TableDefinitionConstructor  extends Constructor {
    public TableDefinitionConstructor() {
        this.yamlConstructors.put(new Tag("TableDefinition"), new ConstructTableDefinition());
        this.yamlConstructors.put(new Tag("TupleDefinition"), new ConstructTupleDefinition());
        this.yamlConstructors.put(new Tag("PartitioningSpec"), new ConstructPartitioningSpec());
    }

    private class ConstructTableDefinition extends AbstractConstruct {
        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Override
        public Object construct(Node node) {
            Map<String, Object> m = (Map) constructMapping((MappingNode)node);
            TupleDefinition keyDef=(TupleDefinition) m.get(K_keyDef);
            TupleDefinition valueDef=(TupleDefinition) m.get(K_valueDef);
            Map<String, BiMap<String,Short>> enumValues=new HashMap<String, BiMap<String, Short>>();
            if(m.containsKey(K_enumValue)) {
                Map<String, Map<String, Integer>> t=(Map)m.get(K_enumValue);
                for(Entry<String, Map<String, Integer>> e:t.entrySet()) {
                    BiMap<String, Short> b=HashBiMap.create();
                    for(Entry<String,Integer> e1:e.getValue().entrySet()) {
                        b.put(e1.getKey(), (short)(int)e1.getValue());
                    }
                    enumValues.put(e.getKey(), b);
                }
            }
            TableDefinition tdef=new TableDefinition(keyDef, valueDef, enumValues);
            if(m.containsKey(K_histogram)) {
                List<String> h=(List<String>)m.get(K_histogram);
                try {
                    tdef.setHistogramColumns(h);
                } catch (StreamSqlException e) {
                    throw new RuntimeException(e);
                }
            }
            if(m.containsKey(K_dataDir)) {
                tdef.setCustomDataDir(true);
                tdef.setDataDir((String)m.get(K_dataDir));
            }
            try {
                if(m.containsKey(K_partitioningSpec)) {
                    tdef.setPartitioningSpec((PartitioningSpec)m.get(K_partitioningSpec));
                } else {
                    PartitioningSpec ps = PartitioningSpec.noneSpec();
                    tdef.setPartitioningSpec(ps);
                }
            } catch (StreamSqlException e) {
                throw new RuntimeException(e);
            }
            if(m.containsKey(K_compressed)) {
                tdef.setCompressed((Boolean)m.get(K_compressed));
            }
            if(m.containsKey(K_storageEngine)) {
                tdef.setStorageEngineName((String)m.get(K_storageEngine));
            } else {//before the storageEngine has been invented, we only had TokyoCabinet, so assume that if it's not set then TokyoCabine is used
                tdef.setStorageEngineName(YarchDatabase.TC_ENGINE_NAME);
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
                if((o1==null) || !(o1 instanceof Integer)) throw new RuntimeException("idx not specified or not integer");
                int idx=(Integer)o1;
                if(idx>TupleDefinition.MAX_COLS) throw new RuntimeException("got idx="+idx+" but max_cols="+TupleDefinition.MAX_COLS);
                String name=(String)m.get("name");
                if(name==null) throw new RuntimeException("name not specifie for column with index idx="+idx);
                DataType type=DataType.valueOf((String)m.get("type"));

                ColumnDefinition cd=new ColumnDefinition(name, type);

                for(int i=cols.size();i<idx+1;i++) 	cols.add(null);
                cols.set(idx, cd);
            }
            TupleDefinition td=new TupleDefinition();
            for(int i=0;i<cols.size();i++) {
                ColumnDefinition cd=cols.get(i);
                if(cd==null) throw new RuntimeException("Column with idx "+i+" not specified");
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
            if(!m.containsKey("type")) throw new RuntimeException("partitioning spec type not specified");

            PartitioningSpec pspec;
            PartitioningSpec._type type = PartitioningSpec._type.valueOf((String)m.get("type"));
            if(type==_type.NONE) {
                pspec = PartitioningSpec.noneSpec();
            } else if(type==_type.TIME) {
                String timeColumn = (String)m.get(K_timeColumn);
                pspec = PartitioningSpec.timeSpec(timeColumn);
            } else if((type==_type.VALUE)) {
                String valueColumn = (String)m.get(K_valueColumn);
                pspec = PartitioningSpec.valueSpec(valueColumn);
            } else if(type==_type.TIME_AND_VALUE) {
                String timeColumn = (String)m.get(K_timeColumn);
                String valueColumn = (String)m.get(K_valueColumn);
                pspec = PartitioningSpec.timeAndValueSpec(timeColumn, valueColumn);				
            } else {
                throw new RuntimeException("Unkwnon partitioning type "+type);
            }


            if(m.containsKey(K_timePartitioningSchema)) {
                pspec.setTimePartitioningSchema((String)m.get(K_timePartitioningSchema));
            } else {
                pspec.setTimePartitioningSchema("YYYY/DOY");
            }
            return pspec;
        }
    }
}