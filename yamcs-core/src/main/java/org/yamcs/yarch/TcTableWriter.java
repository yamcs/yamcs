package org.yamcs.yarch;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;


public class TcTableWriter extends TableWriter {

    
    
	Logger log=LoggerFactory.getLogger(this.getClass().getName());
	Map<String, HistogramDb> column2HistoDb=new HashMap<String, HistogramDb>();
	
	
	public TcTableWriter(YarchDatabase ydb, TableDefinition tableDefinition, InsertMode mode) throws FileNotFoundException, ConfigurationException {
		super(ydb, tableDefinition, mode);
	}

	@Override
    public void onTuple(Stream s, Tuple t) {
		try {
			YBDB db=getDatabase(t);
			boolean inserted=false; 
			
			switch (mode) {
			case INSERT:
			    inserted=insert(db,t);
			    break;
		/*	case UPSERT:
			    upsert(db,t);
                break;*/
			case INSERT_APPEND:
			   inserted=insertAppend(db,t);
			   break;
			/*case UPSERT_APPEND:
			    upsertAppend(db,t);
			    break;*/
			}
			ydb.getTCBFactory().dispose(db);
			if(inserted && tableDefinition.hasHistogram()) {
			    addHistogram(t);
			}
		} catch (IOException e) {
			log.error("failed to insert a record: ", e);
			e.printStackTrace();
		}
	}

	

    private boolean insert(YBDB db, Tuple t) throws IOException {
	    byte[] k=tableDefinition.serializeKey(t);
	    byte[] v=tableDefinition.serializeValue(t);
	    return db.putkeep(k, v);
	}
	/* commented out because not tested TODO
	private void upsert(YBDB db, Tuple t) throws IOException {
        byte[] k=tableDefinition.serializeKey(t);
        byte[] v=tableDefinition.serializeValue(t);
        db.put(k, v);
    }*/
    
	/**
	 * returns true if a new record has been inserted and false if an record was already existing with this key (even if modified)
	 */
	private boolean insertAppend(YBDB db, Tuple t) throws IOException {
        byte[] k=tableDefinition.serializeKey(t);
        byte[] v=db.get(k);
        boolean inserted=false;
        if(v!=null) {//append to an existing row
            Tuple oldt=tableDefinition.deserialize(k, v);
            TupleDefinition tdef=t.getDefinition();
            TupleDefinition oldtdef=oldt.getDefinition();
            
            boolean changed=false;
            ArrayList<Object> cols=new ArrayList<Object>(oldt.getColumns().size()+t.getColumns().size());
            cols.addAll(oldt.columns);
            for(ColumnDefinition cd:tdef.getColumnDefinitions()) {
                if(!oldtdef.hasColumn(cd.getName())) {
                    oldtdef.addColumn(cd);
                    cols.add(t.getColumn(cd.getName()));
                    changed=true;
                }
            }
            if(changed) {
                oldt.columns=cols;
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
	
	/* TODO
	private void upsertAppend(YBDB db, Tuple t) throws IOException {
    }
    */
	
	private YBDB getDatabase(Tuple t) throws IOException {
		String filename=tableDefinition.getDbFilename(t);
		return ydb.getTCBFactory().getTcb(filename+".tcb", tableDefinition.isCompressed(), false);
	}
	
	
	
	private void addHistogram(Tuple t) throws IOException {
        List<String> histoColumns=tableDefinition.getHistogramColumns();
        for(String c: histoColumns) {
            if(!t.hasColumn(c)) continue;
            long time=(Long)t.getColumn(0);
            ColumnSerializer cs=tableDefinition.getColumnSerializer(c);
            byte[] v=cs.getByteArray(t.getColumn(c));
            HistogramDb histodb=column2HistoDb.get(c);
            if(histodb==null) {
                String filename=tableDefinition.getHistogramDbFilename(c)+".tcb";
                histodb=HistogramDb.getInstance(ydb, filename);
                column2HistoDb.put(c, histodb);
            }
            histodb.addValue(v, time);
        }
    }

	/**
	 * called when the stream that provides our input is closed.
	*/
	@Override
    public void streamClosed(Stream stream) {
	
	}
	
	@Override
    public String toString() {
	    return "TcTableWriter["+tableDefinition.getName()+"]";
	}
	
}
