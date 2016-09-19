package org.yamcs.yarch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yaml.snakeyaml.Yaml;

import com.google.common.collect.BiMap;

public class TableDefinitionSerializationTest extends YarchTestCase {

    private void assertDefEquals(String name, DataType dt,  ColumnDefinition cd) {
        assertEquals(name, cd.getName());
        assertEquals(dt, cd.getType());
    }

    private void assertPsEquals(PartitioningSpec ps1in, PartitioningSpec ps1out) {
        assertEquals(ps1in.type, ps1out.type);
        switch(ps1in.type){
        case TIME:
            assertEquals(ps1in.timeColumn, ps1out.timeColumn);
            break;
        case VALUE:
            assertEquals(ps1in.valueColumn, ps1out.valueColumn);
            break;
        case TIME_AND_VALUE:
            assertEquals(ps1in.timeColumn, ps1out.timeColumn);
            assertEquals(ps1in.valueColumn, ps1out.valueColumn);
            assertEquals(ps1in.getValueColumnType(), ps1out.getValueColumnType());
            break;
        case NONE:
            break;
        }

    }

    @Test
    public void testPartitioningSpecSerialization() {
        Yaml yaml=new Yaml(new TableDefinitionConstructor(), new TableDefinitionRepresenter());
        PartitioningSpec ps1in=PartitioningSpec.timeSpec("ttt1");
        PartitioningSpec ps1out=(PartitioningSpec) yaml.load(yaml.dump(ps1in));
        assertPsEquals(ps1in, ps1out);

        ps1in = PartitioningSpec.valueSpec("vvv2");
        ps1out=(PartitioningSpec) yaml.load(yaml.dump(ps1in));
        assertPsEquals(ps1in, ps1out);

        ps1in = PartitioningSpec.timeAndValueSpec("ttt3", "vvv4");
        ps1out=(PartitioningSpec) yaml.load(yaml.dump(ps1in));
        assertPsEquals(ps1in, ps1out);

    }



    @Test
    public void testTableDefinitionSerialization() throws Exception {
        ydb.execute("create table abcde1(aak1 timestamp, aak2 int, aav1 string, aav2 binary, aav3 enum, primary key(aak1, aak2)) histogram(aak2, aav1) partition by time(aak1('YYYY')) table_format=compressed");
        TableDefinition td1=ydb.getTable("abcde1");

        PartitioningSpec pspec = td1.getPartitioningSpec();
        assertNotNull(pspec);		
        assertEquals(TimePartitionSchema.YYYY.class , pspec.getTimePartitioningSchema().getClass() );

        TupleDefinition tplDef=td1.getTupleDefinition().copy();
        tplDef.addColumn("bbv1", DataType.DOUBLE);
        Tuple t=new Tuple(tplDef, new Object[]{1000, 10, "aaaa", new byte[0], "xyz", 3.3d});
        //this should add the bbb1 column and write the definition to disk
        td1.serializeValue(t);

        String cmd="cp "+ydb.getRoot()+"/abcde1.def "+ydb.getRoot()+"/abcde2.def";
        Process p=Runtime.getRuntime().exec(cmd);
        assertEquals(0, p.waitFor());

        ydb.loadTables();
        TableDefinition td2=ydb.getTable("abcde2");
        assertDefEquals("aak1", DataType.TIMESTAMP, td2.getColumnDefinition("aak1"));
        assertDefEquals("aak2", DataType.INT, td2.getColumnDefinition("aak2"));
        assertDefEquals("aav1", DataType.STRING, td2.getColumnDefinition("aav1"));
        assertDefEquals("aav2", DataType.BINARY, td2.getColumnDefinition("aav2"));
        assertDefEquals("aav3", DataType.ENUM, td2.getColumnDefinition("aav3"));
        assertDefEquals("bbv1", DataType.DOUBLE, td2.getColumnDefinition("bbv1"));
        assertEquals(ydb.getDefaultStorageEngineName(), td2.getStorageEngineName());
        
        assertTrue(td2.hasHistogram());
        List<String> al=td2.getHistogramColumns();
        assertEquals(2, al.size());
        assertEquals("aak2", al.get(0));
        assertEquals("aav1", al.get(1));


        PartitioningSpec ps=td2.getPartitioningSpec();
        assertEquals(PartitioningSpec._type.TIME, ps.type);
        assertEquals("aak1", ps.timeColumn);
        assertEquals(TimePartitionSchema.YYYY.class , ps.getTimePartitioningSchema().getClass() );



        BiMap<String, Short>ev =td2.getEnumValues("aav3");
        assertNotNull(ev);
        assertEquals("xyz", ev.inverse().get((short)0));

        assertTrue(td2.isCompressed());


        tplDef=new TupleDefinition();
        tplDef.addColumn("aak1", DataType.TIMESTAMP);
        tplDef.addColumn("aak2", DataType.INT);
        tplDef.addColumn("ccv1", DataType.ENUM);
        tplDef.addColumn("aav3", DataType.ENUM);
        t=new Tuple(tplDef, new Object[]{1001, 10, "uvw", "aav3-second"});
        //this should add the bbb1 column and write the definition to disk
        td2.serializeValue(t);


        cmd="cp "+ydb.getRoot()+"/abcde2.def "+ydb.getRoot()+"/abcde3.def";
        p=Runtime.getRuntime().exec(cmd);
        assertEquals(0, p.waitFor());

        ydb.loadTables();
        TableDefinition td3=ydb.getTable("abcde3");

        ev =td3.getEnumValues("ccv1");
        assertNotNull(ev);
        assertEquals("uvw", ev.inverse().get((short)0));

        ev =td3.getEnumValues("aav3");
        assertNotNull(ev);
        assertEquals((short)1, (short)(Short)ev.get("aav3-second"));
    }




    @Test
    public void testTableDefinitionSerializationTimeAndValue() throws Exception {
        ydb.execute("create table abcde1(aak1 timestamp, aak2 int, aav1 string, aav2 binary, aav3 enum, primary key(aak1, aak2)) histogram(aak2, aav1) partition by time_and_value(aak1('YYYY'), aak2) table_format=compressed");
        TableDefinition td1=ydb.getTable("abcde1");

        PartitioningSpec pspec = td1.getPartitioningSpec();
        assertNotNull(pspec);           
        assertEquals(TimePartitionSchema.YYYY.class , pspec.getTimePartitioningSchema().getClass() );

        TupleDefinition tplDef=td1.getTupleDefinition().copy();
        tplDef.addColumn("bbv1", DataType.DOUBLE);
        Tuple t=new Tuple(tplDef, new Object[]{1000, 10, "aaaa", new byte[0], "xyz", 3.3d});
        //this should add the bbb1 column and write the definition to disk
        td1.serializeValue(t);

        String cmd="cp "+ydb.getRoot()+"/abcde1.def "+ydb.getRoot()+"/abcde2.def";
        Process p=Runtime.getRuntime().exec(cmd);
        assertEquals(0, p.waitFor());

        ydb.loadTables();
        TableDefinition td2 = ydb.getTable("abcde2");
        assertDefEquals("aak1", DataType.TIMESTAMP, td2.getColumnDefinition("aak1"));
        assertDefEquals("aak2", DataType.INT, td2.getColumnDefinition("aak2"));
        assertDefEquals("aav1", DataType.STRING, td2.getColumnDefinition("aav1"));
        assertDefEquals("aav2", DataType.BINARY, td2.getColumnDefinition("aav2"));
        assertDefEquals("aav3", DataType.ENUM, td2.getColumnDefinition("aav3"));
        assertDefEquals("bbv1", DataType.DOUBLE, td2.getColumnDefinition("bbv1"));
        assertEquals(ydb.getDefaultStorageEngineName(), td2.getStorageEngineName());
        
        assertTrue(td2.hasHistogram());
        List<String> al=td2.getHistogramColumns();
        assertEquals(2, al.size());
        assertEquals("aak2", al.get(0));
        assertEquals("aav1", al.get(1));

        assertPsEquals(td1.getPartitioningSpec(), td2.getPartitioningSpec());


        BiMap<String, Short>ev =td2.getEnumValues("aav3");
        assertNotNull(ev);
        assertEquals("xyz", ev.inverse().get((short)0));

        assertTrue(td2.isCompressed());


        tplDef=new TupleDefinition();
        tplDef.addColumn("aak1", DataType.TIMESTAMP);
        tplDef.addColumn("aak2", DataType.INT);
        tplDef.addColumn("ccv1", DataType.ENUM);
        tplDef.addColumn("aav3", DataType.ENUM);
        t=new Tuple(tplDef, new Object[]{1001, 10, "uvw", "aav3-second"});
        //this should add the bbb1 column and write the definition to disk
        td2.serializeValue(t);


        cmd="cp "+ydb.getRoot()+"/abcde2.def "+ydb.getRoot()+"/abcde3.def";
        p=Runtime.getRuntime().exec(cmd);
        assertEquals(0, p.waitFor());

        ydb.loadTables();
        TableDefinition td3=ydb.getTable("abcde3");

        ev = td3.getEnumValues("ccv1");
        assertNotNull(ev);
        assertEquals("uvw", ev.inverse().get((short)0));

        ev =td3.getEnumValues("aav3");
        assertNotNull(ev);
        assertEquals((short)1, (short)(Short)ev.get("aav3-second"));
    }


    @Test
    public void testTableDefinitionSerializationB() throws Exception {
        ydb.execute("create table testb(aaa1 timestamp, aaa2 protobuf('org.yamcs.protobuf.Yamcs$Event'), primary key(aaa1))");
        TableDefinition td=ydb.getTable("testb");

        TupleDefinition tplDef=new TupleDefinition();
        tplDef.addColumn("aaa1", DataType.TIMESTAMP);
        tplDef.addColumn("aaa2", DataType.protobuf(Event.class.getName()));
        tplDef.addColumn("bbb1", DataType.protobuf(NamedObjectId.class.getName()));

        Event e=Event.newBuilder().setSource("testb").setGenerationTime(0)
                .setReceptionTime(0).setSeqNumber(1).setMessage("blab lab").build();
        NamedObjectId id=NamedObjectId.newBuilder().setName("testb").build();
        Tuple t=new Tuple(tplDef, new Object[]{1001, e, id});
        td.serializeValue(t);


        String cmd="cp "+ydb.getRoot()+"/testb.def "+ydb.getRoot()+"/testb1.def";
        Process p=Runtime.getRuntime().exec(cmd);
        assertEquals(0, p.waitFor());
        ydb.loadTables();

        TableDefinition td1=ydb.getTable("testb1");

        ColumnDefinition cd=td1.getColumnDefinition("aaa2");
        assertEquals(cd.getType().val, DataType._type.PROTOBUF);
        assertEquals(e.getClass().getName(), cd.getType().getClassName());


        cd=td1.getColumnDefinition("bbb1");
        assertEquals(cd.getType().val, DataType._type.PROTOBUF);
        assertEquals(id.getClass().getName(), cd.getType().getClassName());
    }
}
