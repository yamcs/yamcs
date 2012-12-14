package org.yamcs.yarch.management;


public interface TableControl {
    /**
     * Returns the table name
     */
    String getName();
   
    /**
     * Returns the tuple definition
     */
    String getSchema();
    /**
     * Returns the primary key
     */
    String getPrimaryKey();
    
    /**
     * 
     * @return partitioning specification (null if there is no partitioning)
     */
    String getPartitioningSpec();
  
}
