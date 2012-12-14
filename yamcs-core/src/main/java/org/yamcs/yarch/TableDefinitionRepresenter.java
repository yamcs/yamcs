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
            m.put("compressed", td.isCompressed());
            m.put("keyDef", td.getKeyDefinition());
            m.put("valueDef", td.serializedValueDef);
            if(td.hasHistogram()) {
                m.put("histogram", td.getHistogramColumns());
            }
            if(td.serializedEmumValues!=null) {
                m.put("enumValues", td.serializedEmumValues);
            }
            if(td.hasCustomDataDir()) {
            	m.put("dataDir", td.getDataDir());
            }
            if(td.hasPartitioning()) {
            	m.put("partitioningSpec", td.getPartitioningSpec());
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
            }
            if((p.type==_type.VALUE) || (p.type==_type.TIME_AND_VALUE)) {
            	m.put("valueColumn", p.valueColumn);
            }
            return representMapping(new Tag("PartitioningSpec"), m, true);
        }
    }
}
