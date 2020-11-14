package org.yamcs.yarch.rocksdb;

import java.io.IOException;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.HistogramInfo;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.PartitionManager;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord.Type;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TimeBasedPartition;

import com.google.protobuf.ByteString;

import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TimePartitionInfo;
import org.yamcs.yarch.YarchException;

/**
 * Handles partitions for one table. All partitions are stored as records in the tablespace.
 * 
 */
public class RdbPartitionManager extends PartitionManager {
    static Logger log = LoggerFactory.getLogger(RdbPartitionManager.class.getName());
    String yamcsInstance;
    final RdbTable table;

    public RdbPartitionManager(RdbTable table, String yamcsInstance, TableDefinition tableDefinition) {
        super(tableDefinition);
        this.table = table;
        this.yamcsInstance = yamcsInstance;
    }

    /**
     * Called at startup to read existing partitions
     * 
     * @throws IOException
     * @throws RocksDBException
     */
    public void readPartitions() throws RocksDBException, IOException {
        ColumnValueSerializer cvs = new ColumnValueSerializer(tableDefinition);
        Tablespace tablespace = table.getTablespace();
        for (TablespaceRecord tr : tablespace.getTablePartitions(yamcsInstance, tableDefinition.getName())) {
            if (tr.hasPartitionValue()) {
                if (tr.hasPartition()) {
                    TimePartitionInfo pinfo = partitioningSpec.getTimePartitioningSchema()
                            .parseDir(tr.getPartition().getPartitionDir());
                    if (pinfo == null) {
                        log.warn("Cannot parse partition from {}, ignoring", tr.getPartition());
                        continue;
                    }
                    addPartitionByTimeAndValue(tr.getTbsIndex(), pinfo,
                            cvs.byteArrayToObject(tr.getPartitionValue().toByteArray()));
                } else {
                    addPartitionByValue(tr.getTbsIndex(), cvs.byteArrayToObject(tr.getPartitionValue().toByteArray()));
                }
            } else {
                if (tr.hasPartition()) {
                    addPartitionByTime(tr.getTbsIndex(), tr.getPartition());
                } else {
                    addPartitionByNone(tr.getTbsIndex());
                }
            }
        }

        for (TablespaceRecord tr : tablespace.getTableHistograms(yamcsInstance, tableDefinition.getName())) {
            if (tr.hasPartition()) {
                addHistogramByTime(tr);
            } else {
                addHistogramByNone(tr);
            }
        }
    }

    /**
     * Called at startup when reading existing partitions from disk
     */
    private void addPartitionByTime(int tbsIndex, TimeBasedPartition tbp) {
        Interval intv = intervals.getFit(tbp.getPartitionStart());

        if (intv == null) {
            intv = new Interval(tbp.getPartitionStart(), tbp.getPartitionEnd());
            intv = intervals.insert(intv);
        }
        Partition p = new RdbPartition(tbsIndex, tbp.getPartitionStart(), tbp.getPartitionEnd(), null,
                tbp.getPartitionDir());
        intv.addTimePartition(p);
    }

    /**
     * Called at startup when reading existing histograms
     */
    private void addHistogramByTime(TablespaceRecord tr) {
        TimeBasedPartition tbp = tr.getPartition();
        Interval intv = intervals.getFit(tbp.getPartitionStart());

        if (intv == null) {
            intv = new Interval(tbp.getPartitionStart(), tbp.getPartitionEnd());
            intv = intervals.insert(intv);
        }
        RdbHistogramInfo hinfo = new RdbHistogramInfo(tr.getTbsIndex(), tr.getHistogramColumnName(),
                tbp.getPartitionDir());
        intv.addHistogram(tr.getHistogramColumnName(), hinfo);
    }

    /**
     * Called at startup when reading existing histograms
     */
    private void addHistogramByNone(TablespaceRecord tr) {
        RdbHistogramInfo hinfo = new RdbHistogramInfo(tr.getTbsIndex(), tr.getHistogramColumnName(), null);
        pcache.addHistogram(tr.getHistogramColumnName(), hinfo);
    }

    /**
     * Called at startup when reading existing partitions from disk
     */
    private void addPartitionByTimeAndValue(int tbsIndex, TimePartitionInfo pinfo, Object value) {
        Interval intv = intervals.getFit(pinfo.getStart());

        if (intv == null) {
            intv = new Interval(pinfo.getStart(), pinfo.getEnd());
            intv = intervals.insert(intv);
        }
        Partition p = new RdbPartition(tbsIndex, pinfo.getStart(), pinfo.getEnd(), value, pinfo.getDir());
        intv.add(value, p);
    }

    /**
     * Called at startup when reading existing partitions from disk
     */
    private void addPartitionByValue(int tbsIndex, Object value) {
        Partition p = new RdbPartition(tbsIndex, Long.MIN_VALUE, Long.MAX_VALUE, value, null);
        pcache.add(value, p);
    }

    /**
     * Called at startup when reading existing partitions from disk
     */
    private void addPartitionByNone(int tbsIndex) {
        Partition p = new RdbPartition(tbsIndex, Long.MIN_VALUE, Long.MAX_VALUE, null, null);
        pcache.add(null, p);
    }

    @Override
    protected Partition createPartitionByTime(TimePartitionInfo pinfo, Object value) throws IOException {
        byte[] bvalue = null;
        if (value != null) {
            ColumnValueSerializer cvs = new ColumnValueSerializer(tableDefinition);
            bvalue = cvs.objectToByteArray(value);
        }
        TablespaceRecord.Builder trb = TablespaceRecord.newBuilder().setType(TablespaceRecord.Type.TABLE_PARTITION)
                .setTableName(tableDefinition.getName()).setPartition(TimeBasedPartition.newBuilder()
                        .setPartitionDir(pinfo.getDir()).setPartitionStart(pinfo.getStart())
                        .setPartitionEnd(pinfo.getEnd())
                        .build());
        if (bvalue != null) {
            trb.setPartitionValue(ByteString.copyFrom(bvalue));
        }
        try {
            TablespaceRecord tr = table.getTablespace().createMetadataRecord(yamcsInstance, trb);
            return new RdbPartition(tr.getTbsIndex(), pinfo.getStart(), pinfo.getEnd(), value, pinfo.getDir());
        } catch (RocksDBException e) {
            throw new IOException(e);
        }

    }

    @Override
    protected Partition createPartition(Object value) {
        String tblName = tableDefinition.getName();
        byte[] bvalue = null;
        if (value != null) {
            ColumnValueSerializer cvs = new ColumnValueSerializer(tableDefinition);
            bvalue = cvs.objectToByteArray(value);
        }
        TablespaceRecord.Builder trb = TablespaceRecord.newBuilder()
                .setType(Type.TABLE_PARTITION).setTableName(tblName);
        if (bvalue != null) {
            trb.setPartitionValue(ByteString.copyFrom(bvalue));
        }
        try {
            TablespaceRecord tr = table.getTablespace().createMetadataRecord(yamcsInstance, trb);
            return new RdbPartition(tr.getTbsIndex(), Long.MIN_VALUE, Long.MAX_VALUE, value, null);
        } catch (RocksDBException e) {
            throw new YarchException(e);
        }

    }

    /**
     * For time partitioned tables: creates a tablespace record for the histogram for the given column name 
     */
    @Override
    protected HistogramInfo createHistogramByTime(TimePartitionInfo pinfo, String columnName) {
        try {
            TablespaceRecord.Builder trb = TablespaceRecord.newBuilder().setType(Type.HISTOGRAM)
                    .setTableName(tableDefinition.getName())
                    .setHistogramColumnName(columnName)
                    .setPartition(TimeBasedPartition.newBuilder().setPartitionDir(pinfo.getDir())
                            .setPartitionStart(pinfo.getStart()).setPartitionEnd(pinfo.getEnd()).build());

            TablespaceRecord tr = table.getTablespace().createMetadataRecord(yamcsInstance, trb);
            return new RdbHistogramInfo(tr.getTbsIndex(), columnName, pinfo.getDir());
        } catch (RocksDBException e) {
            throw new YarchException(e);
        }
    }

    /**
     * For non time partitioned tables - create a tablespace record for the histogram for the given column name
     */
    @Override
    protected HistogramInfo createHistogram(String columnName) {
        try {
            TablespaceRecord.Builder trb = TablespaceRecord.newBuilder().setType(Type.HISTOGRAM)
                    .setTableName(tableDefinition.getName())
                    .setHistogramColumnName(columnName);
            TablespaceRecord tr = table.getTablespace().createMetadataRecord(yamcsInstance, trb);
            return new RdbHistogramInfo(tr.getTbsIndex(), columnName, null);
        } catch (RocksDBException e) {
            throw new YarchException(e);
        }
    }
}
