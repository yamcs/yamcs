package org.yamcs.yarch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.yarch.PartitioningSpec._type;
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
	public final static String K_compressed = "compressed";
	public final static String K_keyDef = "keyDef";
	public final static String K_valueDef = "valueDef";
	public final static String K_dataDir = "dataDir";
	public final static String K_histogram = "histogram";
	public final static String K_enumValue = "enumValues";
	public final static String K_partitioningSpec = "partitioningSpec";	
	public final static String K_timeColumn = "timeColumn";
	public final static String K_valueColumn = "valueColumn";
	public final static String K_timePartitioningSchema = "timePartitioningSchema";
	public final static String K_storageEngine = "storageEngine";
	
	
    public TableDefinitionRepresenter() {
        this.representers.put(TableDefinition.class, new RepresentTableDefinition());
        this.representers.put(TupleDefinition.class, new RepresentTupleDefinition());
        this.representers.put(PartitioningSpec.class, new RepresentPartitioningSpec());
    }

    private class RepresentTableDefinition implements Represent {
        @Override
        public Node representData(Object data) {
            TableDefinition td = (TableDefinition) data;
            Map<String, Object> m=new HashMap<String, Object>();
            m.put(K_compressed, td.isCompressed());
            m.put(K_keyDef, td.getKeyDefinition());
            m.put(K_valueDef, td.serializedValueDef);
            m.put(K_storageEngine, td.getStorageEngineName());
            
            if(td.hasHistogram()) {
                m.put(K_histogram, td.getHistogramColumns());
            }
            if(td.serializedEmumValues!=null) {
                m.put(K_enumValue, td.serializedEmumValues);
            }
            if(td.hasCustomDataDir()) {
            	m.put(K_dataDir, td.getDataDir());
            }
            if(td.hasPartitioning()) {
            	m.put(K_partitioningSpec, td.getPartitioningSpec());
            }
            return representMapping(new Tag("TableDefinition"), m, false);
        }
    }
    
    private class RepresentTupleDefinition implements Represent {
        @Override
        public Node representData(Object data) {
            TupleDefinition td = (TupleDefinition) data;
            List<Object> list=new ArrayList<Object>(td.size());
        	for(int i=0;i<td.size();i++) {
        		ColumnDefinition cd=td.getColumn(i);
        		Map<String, Object> m=new HashMap<String, Object>();
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
            Map<String, Object> m=new HashMap<String, Object>();
            m.put("type", p.type.toString());
            if((p.type==_type.TIME) || (p.type==_type.TIME_AND_VALUE)) {
            	m.put("timeColumn", p.timeColumn);
            	m.put(K_timePartitioningSchema, p.getTimePartitioningSchema().getName());
            }
            if((p.type==_type.VALUE) || (p.type==_type.TIME_AND_VALUE)) {
            	m.put(K_valueColumn, p.valueColumn);
            }
            
            
            return representMapping(new Tag("PartitioningSpec"), m, true);
        }
    }
}
