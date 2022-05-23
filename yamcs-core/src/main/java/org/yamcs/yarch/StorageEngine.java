package org.yamcs.yarch;

import java.util.List;

import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.TableWriter.InsertMode;

public interface StorageEngine {
    /**
     * Loads the table definitions from the disk for all the tables belonging to the instance.
     * <p>
     * called at startup.
     * 
     * @param ydb
     * @throws YarchException
     */
    public List<TableDefinition> loadTables(YarchDatabaseInstance ydb) throws YarchException;

    /**
     * Create a new table based on definition.
     * 
     * @param ydb
     * @param tblDef
     * @throws YarchException
     */
    public void createTable(YarchDatabaseInstance ydb, TableDefinition tblDef) throws YarchException;

    /**
     * Persist the table definition to diks (called when the table definition modifies)
     * 
     * <p>
     * The general table properties should be read from the tblDef argument but the column properties should be read
     * from the extra arguments This is because the method is called with modified column content which is not reflected
     * in the table definition until the data is saved in the database.
     * 
     * @param ydb
     * @param tblDef
     * @throws YarchException
     */
    public void saveTableDefinition(YarchDatabaseInstance ydb, TableDefinition tblDef,
            List<TableColumnDefinition> keyColumns,
            List<TableColumnDefinition> valueColumns) throws YarchException;

    /**
     * Drop the table (removing all data)
     * 
     * @param ydb
     * @param tblDef
     * @throws YarchException
     */
    public void dropTable(YarchDatabaseInstance ydb, TableDefinition tblDef) throws YarchException;

    /**
     * 
     * Creates a new table writer
     * 
     * @param ydb
     * @param tblDef
     * @param insertMode
     * @return
     * @throws YarchException
     */
    public TableWriter newTableWriter(YarchDatabaseInstance ydb, TableDefinition tblDef, InsertMode insertMode)
            throws YarchException;

    /**
     * 
     * Creates a new table iterator.
     * 
     * @param ctx
     * @param tblDef
     */
    public TableWalker newTableWalker(ExecutionContext ctx, TableDefinition tblDef, boolean ascending,
            boolean follow);

    public HistogramIterator getHistogramIterator(YarchDatabaseInstance ydb, TableDefinition tblDef, String columnName,
            TimeInterval interval) throws YarchException;

    public BucketDatabase getBucketDatabase(YarchDatabaseInstance yarchDatabaseInstance) throws YarchException;

    public ProtobufDatabase getProtobufDatabase(YarchDatabaseInstance ydb) throws YarchException;

    public PartitionManager getPartitionManager(YarchDatabaseInstance ydb, TableDefinition tblDef);

    /**
     * In Yamcs version 4 the table definitions were stored in yaml serialized format (in the
     * /storage/yamcs-data/<instance-name>/<table-name>.def)
     * 
     * <p>
     * This function is called to migrate them inside the storage engine where they are stored starting with Yamcs 5
     * 
     * @throws YarchException
     */
    default void migrateTableDefinition(YarchDatabaseInstance ydb, TableDefinition tblDef) throws YarchException {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a sequence with the given name if it exists or first create it and returns it if create is true.
     * <p>
     * If create is false and the sequence does not exist, returns null.
     * 
     * @param name
     * @param create
     * @return
     * @throws YarchException
     */
    public Sequence getSequence(YarchDatabaseInstance ydb, String name, boolean create) throws YarchException;

    public TableWalker newSecondaryIndexTableWalker(YarchDatabaseInstance ydb, TableDefinition tableDefinition,
            boolean ascending, boolean follow);

    /**
     * Gets the list of sequences togehter with their latest values
     */
    public List<SequenceInfo> getSequencesInfo(YarchDatabaseInstance ydb);

    /**
     * rename the table
     */
    public void renameTable(YarchDatabaseInstance ydb, TableDefinition tblDef, String newName);

}
