package org.yamcs.yarch;

import java.util.List;

import org.yamcs.archive.TagDb;
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
    public  List<TableDefinition>  loadTables(YarchDatabaseInstance ydb) throws YarchException;

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
     * @param ydb
     * @param tblDef
     * @throws YarchException 
     */
    public void saveTableDefinition(YarchDatabaseInstance ydb, TableDefinition tblDef) throws YarchException;

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
     * Creates a new table reader.
     * 
     * @param ydb
     * @param tblDef
     */
    public Stream newTableReaderStream(YarchDatabaseInstance ydb, TableDefinition tblDef, boolean ascending,
            boolean follow);

    public TagDb getTagDb(YarchDatabaseInstance ydb) throws YarchException;

    public HistogramIterator getHistogramIterator(YarchDatabaseInstance ydb, TableDefinition tblDef, String columnName,
            TimeInterval interval) throws YarchException;

    public BucketDatabase getBucketDatabase(YarchDatabaseInstance yarchDatabaseInstance) throws YarchException;

    public ProtobufDatabase getProtobufDatabase(YarchDatabaseInstance ydb) throws YarchException;

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

}
