package org.yamcs.yarch.rocksdb2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.ColumnSerializer;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.rocksdb.RdbHistogramDb;

public class RdbTableWriter extends TableWriter {
    private final RdbPartitionManager partitionManager;
    private final PartitioningSpec partitioningSpec;
    Logger log=LoggerFactory.getLogger(this.getClass().getName());
    RDBFactory rdbFactory; 
    RdbHistogramDb histodb;

    public RdbTableWriter(YarchDatabase ydb, TableDefinition tableDefinition, InsertMode mode, RdbPartitionManager pm) throws IOException {
        super(ydb, tableDefinition, mode);
        this.partitioningSpec = tableDefinition.getPartitioningSpec();
        this.partitionManager = pm;
        rdbFactory = RDBFactory.getInstance(ydb.getName());
        if(tableDefinition.hasHistogram()) {			 
            String filename=tableDefinition.getDataDir()+"/"+tableDefinition.getName()+"-histo";
            histodb = new RdbHistogramDb(ydb, filename, false);
        }
    }

    @Override
    public void onTuple(Stream stream, Tuple t) {
        try {
            RdbPartition partition = getDbPartition(t);
            YRDB db = rdbFactory.getRdb(tableDefinition.getDataDir()+"/"+partition.dir, false);

            boolean inserted=false;
            boolean updated=false;

            switch (mode) {
            case INSERT:
                inserted = insert(db, partition, t);
                break;
            case UPSERT:
                inserted = upsert(db, partition, t);
                updated=!inserted;
                break;
            case INSERT_APPEND:
                inserted=insertAppend(db, partition, t);
                break;
            case UPSERT_APPEND:
                inserted=upsertAppend(db, partition, t);
                updated=!inserted;
                break;
            }
            rdbFactory.dispose(db);
            if(inserted && tableDefinition.hasHistogram()) {
                addHistogram(t);
            }
            if(updated && tableDefinition.hasHistogram()) {
                // TODO updateHistogram(t);
            }
        } catch (IOException e) {
            log.error("failed to insert a record: ", e);
            e.printStackTrace();
        } catch (RocksDBException e) {
            log.error("failed to insert a record: ", e);
            e.printStackTrace();
        }

    }

    private void addHistogram(Tuple t) throws IOException {
        List<String> histoColumns=tableDefinition.getHistogramColumns();
        for(String c: histoColumns) {
            if(!t.hasColumn(c)) continue;
            long time=(Long)t.getColumn(0);
            ColumnSerializer cs=tableDefinition.getColumnSerializer(c);
            byte[] v=cs.getByteArray(t.getColumn(c));
            histodb.addValue(c, v, time);
        }
    }

    @Override
    public void streamClosed(Stream stream) {
        // TODO Auto-generated method stub

    }


    private boolean insert(YRDB db, RdbPartition partition, Tuple t) throws RocksDBException {
        byte[] k = getPartitionKey(partition, tableDefinition.serializeKey(t));
        byte[] v=tableDefinition.serializeValue(t);

        if(db.get(k)==null) {
            db.put(k, v);
            return true;
        } else {
            return false;
        }
    }

    private boolean upsert(YRDB db, RdbPartition partition, Tuple t) throws RocksDBException {
        byte[] k = getPartitionKey(partition, tableDefinition.serializeKey(t));
        byte[] v = tableDefinition.serializeValue(t);

        if(db.get(k)==null) {
            db.put(k, v);
            return true;
        } else {
            db.put(k, v);
            return false;
        }
    }


    /**
     * returns true if a new record has been inserted and false if an record was already existing with this key (even if modified)
     * @param partition 
     * @throws RocksDBException 
     */
    private boolean insertAppend(YRDB db, RdbPartition partition, Tuple t) throws RocksDBException {
        byte[] k = getPartitionKey(partition, tableDefinition.serializeKey(t));
        byte[] v=db.get(k);
        boolean inserted=false;
        if(v!=null) {//append to an existing row
            Tuple oldt=tableDefinition.deserialize(k, v);
            TupleDefinition tdef=t.getDefinition();
            TupleDefinition oldtdef=oldt.getDefinition();

            boolean changed=false;
            ArrayList<Object> cols=new ArrayList<Object>(oldt.getColumns().size()+t.getColumns().size());
            cols.addAll(oldt.getColumns());
            for(ColumnDefinition cd:tdef.getColumnDefinitions()) {
                if(!oldtdef.hasColumn(cd.getName())) {
                    oldtdef.addColumn(cd);
                    cols.add(t.getColumn(cd.getName()));
                    changed=true;
                }
            }
            if(changed) {
                oldt.setColumns(cols);
                v=tableDefinition.serializeValue(oldt);
                db.put(k, v);
            }
        } else {//new row
            inserted=true;
            v=tableDefinition.serializeValue(t);
            db.put(k, v);
        }
        return inserted;
    }

    private boolean upsertAppend(YRDB db, RdbPartition partition, Tuple t) throws RocksDBException {
        byte[] k = getPartitionKey(partition, tableDefinition.serializeKey(t));
       
        byte[] v = db.get(k);
        boolean inserted=false;
        if(v!=null) {//append to an existing row
            Tuple oldt=tableDefinition.deserialize(k, v);
            TupleDefinition tdef=t.getDefinition();
            TupleDefinition oldtdef=oldt.getDefinition();

            boolean changed=false;
            ArrayList<Object> cols=new ArrayList<Object>(oldt.getColumns().size()+t.getColumns().size());
            cols.addAll(oldt.getColumns());
            for(ColumnDefinition cd:tdef.getColumnDefinitions()) {
                if (oldtdef.hasColumn(cd.getName())) {
                    // currently always says it changed. Not sure if it's worth checking if different
                    cols.set(oldt.getColumnIndex(cd.getName()), t.getColumn(cd.getName()));
                    changed=true;
                } else {
                    oldtdef.addColumn(cd);
                    cols.add(t.getColumn(cd.getName()));
                    changed=true;
                }
            }
            if(changed) {
                oldt.setColumns(cols);
                v=tableDefinition.serializeValue(oldt);
                db.put(k, v);
            }
        } else {//new row
            inserted=true;
            v=tableDefinition.serializeValue(t);
            db.put(k, v);
        }
        return inserted;
    }

    //prepends the partition binary value to the key 
    private byte[] getPartitionKey(RdbPartition partition, byte[] k) {
        byte[] p = partition.binaryValue;
        byte[] pk = new byte[p.length+k.length];
        System.arraycopy(p, 0, pk, 0, p.length);
        System.arraycopy(k, 0, pk, p.length, k.length);
        return pk;
    }
    /**
     * get the filename where the tuple would fit (can be a partition)
     * @param t
     * @return the partition where the tuple fits
     * @throws IOException if there was an error while creating the directories where the file should be located
     */
    public RdbPartition getDbPartition(Tuple t) throws IOException {
        long time=TimeEncoding.INVALID_INSTANT;
        Object value=null;
        if(partitioningSpec.timeColumn!=null) {
            time =(Long)t.getColumn(partitioningSpec.timeColumn);
        }
        if(partitioningSpec.valueColumn!=null) {
            value=t.getColumn(partitioningSpec.valueColumn);
            ColumnDefinition cd=tableDefinition.getColumnDefinition(partitioningSpec.valueColumn);
            if(cd.getType()==DataType.ENUM) {
                value = tableDefinition.addAndGetEnumValue(partitioningSpec.valueColumn, (String) value);                
            }
        }
        return (RdbPartition)partitionManager.createAndGetPartition(time, value);
    }
    
    public void close() {
        histodb.close();
    }

}