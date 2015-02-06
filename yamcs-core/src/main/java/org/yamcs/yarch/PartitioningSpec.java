package org.yamcs.yarch;


public class PartitioningSpec {
    public enum _type{
    	NONE, //no partition at all
    	TIME, //partition by time 
    	VALUE, //partition by value
    	TIME_AND_VALUE //partition by time and value (in this order)
    	};
    final public _type type;
    public String timeColumn;
    public String valueColumn;
    public DataType valueColumnType;
    public TimePartitionSchema timePartitioningSchema = TimePartitionSchema.getInstance("YYYY/DOY"); 
    
    public PartitioningSpec (_type type) {
    	this.type=type;
    }
    
    public void setTimePartitioningSchema(String schema) {
    	timePartitioningSchema = TimePartitionSchema.getInstance(schema);
    }
    
    @Override
    public String toString() {
        return "timeColumn: "+timeColumn+" valueColumn:"+valueColumn;
    }
}
