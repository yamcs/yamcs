package org.yamcs.yarch;

import org.yamcs.archive.TagDb;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.TableWriter.InsertMode;

public interface StorageEngine {
    /**
     * Create a new table based on definition.
     * 
     * @param def
     * @throws YarchException
     */
    public void createTable(YarchDatabaseInstance ydb, TableDefinition def) throws YarchException;

    /**
     * Drop the table (removing all files)
     * 
     * @param tbldef
     * @throws YarchException
     */
    public void dropTable(YarchDatabaseInstance ydb, TableDefinition tbldef) throws YarchException;

    /**
     * Loads a table from the disk - called at startup.
     * 
     * @param tbl
     * @throws YarchException
     */
    public void loadTable(YarchDatabaseInstance ydb, TableDefinition tbl) throws YarchException;

    /**
     * 
     * Creates a new table writer
     * 
     * @param tbl
     * @param insertMode
     * @return
     * @throws YarchException
     */
    public TableWriter newTableWriter(YarchDatabaseInstance ydb, TableDefinition tbl, InsertMode insertMode)
            throws YarchException;

    /**
     * 
     * Creates a new table reader
     * 
     * @param tbl
     */
    public Stream newTableReaderStream(YarchDatabaseInstance ydb, TableDefinition tbl, boolean ascending,
            boolean follow);

    public TagDb getTagDb(YarchDatabaseInstance ydb) throws YarchException;

    public HistogramIterator getHistogramIterator(YarchDatabaseInstance ydb, TableDefinition tblDef, String columnName,
            TimeInterval interval, long mergeTime) throws YarchException;

    public BucketDatabase getBucketDatabase(YarchDatabaseInstance yarchDatabaseInstance) throws YarchException;
}
