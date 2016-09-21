package org.yamcs.yarch;

import org.yamcs.archive.TagDb;
import org.yamcs.yarch.TableWriter.InsertMode;

public interface StorageEngine {
    /**
     *  Create a new table based on definition.
     * @param def
     * @throws YarchException 
     */
    public void createTable(TableDefinition def) throws YarchException;

    /**
     * Drop the table (removing all files)
     * @param tbldef
     * @throws YarchException
     */
    public void dropTable(TableDefinition tbldef) throws YarchException;

    /**
     * Loads a table from the disk - called at startup.
     * @param tbl
     * @throws YarchException
     */
    public void loadTable(TableDefinition tbl) throws YarchException;

    /**
     * 
     * Creates a new table writer
     * 
     * @param tbl
     * @param insertMode
     * @return
     * @throws YarchException
     */
    public TableWriter newTableWriter(TableDefinition tbl, InsertMode insertMode) throws YarchException;

    /**
     * 
     * Creates a new table reader
     * @param tbl
     * @return
     */
    public AbstractStream newTableReaderStream(TableDefinition tbl, boolean ascending, boolean follow);

    /**
     * gets the histogram database
     * @param tbl
     * @return
     * @throws YarchException
     */
    public HistogramDb getHistogramDb(TableDefinition tbl) throws YarchException;

    public TagDb getTagDb() throws YarchException;
}
