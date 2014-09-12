package org.yamcs.yarch;

import org.yamcs.yarch.TableWriter.InsertMode;

public interface StorageEngine {
    public void dropTable(TableDefinition tbldef) throws YarchException;
    public TableWriter newTableWriter(TableDefinition tbl, InsertMode insertMode) throws YarchException;
    public void loadTable(TableDefinition tbl) throws YarchException;
    public AbstractStream newTableReaderStream(TableDefinition tbl);
    public void createTable(TableDefinition def);;
}
