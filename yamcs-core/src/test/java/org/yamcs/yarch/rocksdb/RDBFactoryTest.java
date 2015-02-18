package org.yamcs.yarch.rocksdb;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rocksdb.RocksDB;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.PartitioningSpec._type;

public class RDBFactoryTest {	
	@BeforeClass
	public static void initRocksDb() {
		RocksDB.loadLibrary();
	}

	private boolean isOpen(YRDB yrdb) {
		return yrdb.isOpen();	
	}
	TableDefinition getTableDef() throws Exception {
		PartitioningSpec spec=new PartitioningSpec(_type.TIME_AND_VALUE);
		spec.timeColumn="gentime";
        spec.valueColumn="packetid";
        spec.valueColumnType=DataType.INT;
        
		TupleDefinition tdef=new TupleDefinition();
		tdef.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
		tdef.addColumn(new ColumnDefinition("packetid", DataType.INT));


		TableDefinition tblDef = new TableDefinition("tbltest", tdef, Arrays.asList("gentime"));
		tblDef.setPartitioningSpec(spec);

		return tblDef;
	}


	@Test
	public void testDispose() throws Exception {
		TableDefinition tblDef= getTableDef();
		YRDB[] dbs=new YRDB[RDBFactory.maxOpenDbs*2];
		RDBFactory rdbf=new RDBFactory();
		ColumnValueSerializer cvs= new ColumnValueSerializer(tblDef);
		
		for(int i=0;i<RDBFactory.maxOpenDbs;i++) {
			dbs[i]=rdbf.getRdb("/tmp/rdbfactorytest"+i, cvs, false);
		}
		for(int i=0;i<RDBFactory.maxOpenDbs/2;i++) {
			rdbf.dispose(dbs[i]);
		}
		for(int i=0;i<RDBFactory.maxOpenDbs;i++) {
			assertTrue(isOpen(dbs[i]));
		}
		for(int i=RDBFactory.maxOpenDbs;i<2*RDBFactory.maxOpenDbs;i++) {
			dbs[i]=rdbf.getRdb("/tmp/rdbfactorytest"+i, cvs, false);
		}
		for(int i=0;i<RDBFactory.maxOpenDbs/2;i++) {
			assertFalse(isOpen(dbs[i]));
		}
		for(int i=RDBFactory.maxOpenDbs/2;i<2*RDBFactory.maxOpenDbs;i++) {
			assertTrue(isOpen(dbs[i]));
		}
	}	
}
