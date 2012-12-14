package org.yamcs.yarch.management;

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.TableDefinition;


public class TableControlImpl extends StandardMBean implements TableControl{
   TableDefinition table;
    TableControlImpl(TableDefinition table) throws NotCompliantMBeanException {
        super(TableControl.class);
        this.table=table;
    }
    
    @Override
    public String getName() {
        return table.getName();
    }
    
    @Override
    public String getSchema() {
        return table.getTupleDefinition().getStringDefinition();
    }

    @Override
    public String getPrimaryKey() {
        return table.getKeyDefinition().getStringDefinition();
    }

    @Override
    public String getPartitioningSpec() {
        PartitioningSpec pspec=table.getPartitioningSpec();
        if(pspec==null) return "<no partitioning>"; 
        else return table.getPartitioningSpec().toString();
    }
}
