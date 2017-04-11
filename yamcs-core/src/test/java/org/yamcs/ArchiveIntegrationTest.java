package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Test;
import org.yamcs.api.MediaType;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.rest.BulkRestDataReceiver;
import org.yamcs.api.rest.BulkRestDataSender;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.protobuf.Archive.TableData.TableRecord;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Rest.CreateProcessorRequest;
import org.yamcs.protobuf.Rest.EditClientRequest;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Table;
import org.yamcs.protobuf.Table.Cell;
import org.yamcs.protobuf.Table.ColumnInfo;
import org.yamcs.protobuf.Table.Row;
import org.yamcs.protobuf.Table.TableLoadResponse;
import org.yamcs.protobuf.Web.RestExceptionMessage;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.websocket.ParameterResource;
import org.yamcs.yarch.ColumnSerializer;
import org.yamcs.yarch.ColumnSerializerFactory;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpMethod;

public class ArchiveIntegrationTest extends AbstractIntegrationTest {
    
    ColumnSerializer csint = ColumnSerializerFactory.getBasicColumnSerializer(DataType.INT);
    ColumnSerializer csstr = ColumnSerializerFactory.getBasicColumnSerializer(DataType.STRING);
    
    
    static { //to avoid getting the warning in the console in the test below that loads invalid table records
        Logger.getLogger("org.yamcs.yarch").setLevel(Level.SEVERE);    
    }

    private void generateData(String utcStart, int numPackets) {
        long t0 = TimeEncoding.parse(utcStart);
        for (int i=0;i <numPackets; i++) {
            packetGenerator.setGenerationTime(t0+1000*i);
            packetGenerator.generate_PKT1_1();
            packetGenerator.generate_PKT1_3();
            
            //parameters are 10ms later than packets to make sure that we have a predictable order during replay
            parameterProvider.setGenerationTime(t0+1000*i+10);
            parameterProvider.generateParameters(i);
        }
    }

    @Test
    public void testReplay() throws Exception {
        Long t0 = System.currentTimeMillis();
        generateData("2015-01-01T10:00:00", 300);
        
        restClient.setAcceptMediaType(MediaType.JSON);
        restClient.setSendMediaType(MediaType.JSON);

        
        NamedObjectList subscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara1_1_6", "/REFMDB/SUBSYS1/IntegerPara1_1_7", 
                "/REFMDB/SUBSYS1/processed_para_uint", "/REFMDB/SUBSYS1/processed_para_double");
        WebSocketRequest wsr = new WebSocketRequest("parameter", ParameterResource.WSR_subscribe, subscrList);
        wsClient.sendRequest(wsr);
        
        //these are from the realtime processor cache
        ParameterData pdata = wsListener.parameterDataList.poll(2, TimeUnit.SECONDS);
        assertEquals(4, pdata.getParameterCount());
        ParameterValue p1_1_6 = pdata.getParameter(0);
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1_6", p1_1_6.getId().getName());
     //   assertEquals("2015-01-01T10:59:59.000", p1_1_6.getGenerationTimeUTC());
        
        
        ClientInfo cinfo = getClientInfo();

        //create a parameter replay via REST
        CreateProcessorRequest prequest = CreateProcessorRequest.newBuilder()
                .addClientId(cinfo.getId())
                .setName("testReplay")
                .setStart("2015-01-01T10:01:00")
                .setStop("2015-01-01T10:05:00")
                .addPacketname("*")
                .addPpgroup("IntegrationTest")                
                .build();

        restClient.doRequest("/processors/IntegrationTest", HttpMethod.POST, toJson(prequest, SchemaRest.CreateProcessorRequest.WRITE)).get();

        cinfo = getClientInfo();
        assertEquals("testReplay", cinfo.getProcessorName());


        pdata = wsListener.parameterDataList.poll(2, TimeUnit.SECONDS);
        assertNotNull(pdata);

        assertEquals(2, pdata.getParameterCount());
        p1_1_6 = pdata.getParameter(0);
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1_6", p1_1_6.getId().getName());
        assertEquals("2015-01-01T10:01:00.000", p1_1_6.getGenerationTimeUTC());

        
        pdata = wsListener.parameterDataList.poll(2, TimeUnit.SECONDS);
        assertNotNull(pdata);
        assertEquals(2, pdata.getParameterCount());
        ParameterValue pp_para_uint = pdata.getParameter(0);
        assertEquals("/REFMDB/SUBSYS1/processed_para_uint", pp_para_uint.getId().getName());
        assertEquals("2015-01-01T10:01:00.010", pp_para_uint.getGenerationTimeUTC());
        
        ParameterValue pp_para_double = pdata.getParameter(1);
        assertEquals("/REFMDB/SUBSYS1/processed_para_double", pp_para_double.getId().getName());
        assertEquals("2015-01-01T10:01:00.010", pp_para_uint.getGenerationTimeUTC());

        
        pdata = wsListener.parameterDataList.poll(2, TimeUnit.SECONDS);
        assertNotNull(pdata);
        System.out.println("pdata: "+pdata);
        assertEquals(1, pdata.getParameterCount());
        pp_para_uint = pdata.getParameter(0);
        assertEquals("/REFMDB/SUBSYS1/processed_para_uint", pp_para_uint.getId().getName());
        assertEquals("2015-01-01T10:01:00.030", pp_para_uint.getGenerationTimeUTC());
        
        
        pdata = wsListener.parameterDataList.poll(2, TimeUnit.SECONDS);
        assertNotNull(pdata);

        assertEquals(2, pdata.getParameterCount());
        p1_1_6 = pdata.getParameter(0);
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1_6", p1_1_6.getId().getName());
        assertEquals("2015-01-01T10:01:01.000", p1_1_6.getGenerationTimeUTC());

        //go back to realtime
        EditClientRequest pcrequest = EditClientRequest.newBuilder().setProcessor("realtime").build();
        restClient.doRequest("/clients/" + cinfo.getId(), HttpMethod.PATCH, toJson(pcrequest, SchemaRest.EditClientRequest.WRITE)).get();

        cinfo = getClientInfo();
        assertEquals("realtime", cinfo.getProcessorName());
    }

    @Test
    public void testEmptyIndex() throws Exception {
        String response = restClient.doRequest("/archive/IntegrationTest/indexes/packets?start=2035-01-02T00:00:00", HttpMethod.GET, "").get();
        assertTrue(response.isEmpty());
    }

    @Test
    public void testIndexWithRestClient() throws Exception {
        generateData("2015-02-01T10:00:00", 3600);
        List<ArchiveRecord> arlist = new ArrayList<>();
        restClient.setAcceptMediaType(MediaType.PROTOBUF);
        CompletableFuture<Void> f = restClient.doBulkGetRequest("/archive/IntegrationTest/indexes/packets?start=2015-02-01T00:00:00&stop=2015-02-01T11:00:00", new BulkRestDataReceiver() {

            @Override
            public void receiveException(Throwable t) {
                fail(t.getMessage());
            }

            @Override
            public void receiveData(byte[] data) throws YamcsApiException {
                try {
                    arlist.add(ArchiveRecord.parseFrom(data));
                } catch (InvalidProtocolBufferException e) {
                    fail("Cannot decode archive record: "+e);
                }
            }
        });

        f.get();
        assertEquals(4, arlist.size());
    }

    @Test
    public void testParameterHistory() throws Exception {
        generateData("2015-02-02T10:00:00", 3600);
        String respDl = restClient.doRequest("/archive/IntegrationTest/parameters/REFMDB/ccsds-apid?start=2015-02-02T10:10:00&norepeat=true&limit=3", HttpMethod.GET, "").get();

        ParameterData pdata = fromJson(respDl, org.yamcs.protobuf.SchemaPvalue.ParameterData.MERGE).build();
        assertEquals(1, pdata.getParameterCount());
        ParameterValue pv = pdata.getParameter(0);
        assertEquals(995, pv.getEngValue().getUint32Value());

        respDl = restClient.doRequest("/archive/IntegrationTest/parameters/REFMDB/ccsds-apid?start=2015-02-02T10:10:00&norepeat=false&limit=3", HttpMethod.GET, "").get();
        pdata = fromJson(respDl, org.yamcs.protobuf.SchemaPvalue.ParameterData.MERGE).build();
        assertEquals(3, pdata.getParameterCount());
    }


    @Test
    public void testTableLoadDump() throws Exception {
        BulkRestDataSender brds = initiateTableLoad("table0");

        for(int i=0;i<100; i+=4) {
            ByteBuf buf = encode(getRecord(i), getRecord(i+1), getRecord(i+2), getRecord(i+3));
            brds.sendData(buf);
        }
        TableLoadResponse tlr = TableLoadResponse.parseFrom(brds.completeRequest().get());
        assertEquals(100, tlr.getRowsLoaded());

        verifyRecords("table0", 100);
        verifyRecordsDumpFormat("table0", 100);
    }

    @Test
    public void testTableLoadWithInvalidRecord() throws Exception {
        Throwable t1 = null;
        BulkRestDataSender brds = initiateTableLoad("table1");
        try {
            for(int i=0;i<100; i++) {
                    Row tr;
                    if(i!=50) {
                        tr = getRecord(i);
                    } else {
                        Row.Builder trb = Row.newBuilder();
                        trb.addCell(Cell.newBuilder().setColumnId(2).setData(ByteString.copyFrom(csstr.toByteArray("test "+i))).build());
                        tr=trb.build();
                    }
                    brds.sendData(encode(tr));
            }
            brds.completeRequest().get();
        } catch (ExecutionException e) {
            t1 = e.getCause();
        }
        assertNotNull(t1);
        RestExceptionMessage rem = ((YamcsApiException)t1).getRestExceptionMessage();
        assertTrue(rem.hasExtension(Table.rowsLoaded));
        assertEquals(50, (int)rem.getExtension(Table.rowsLoaded));
        verifyRecords("table1", 50);
    }
    
    @Test
    public void testTableLoadWithInvalidRecord2() throws Exception {
        BulkRestDataSender brds = initiateTableLoad("table2");
        Throwable t1 = null;
        
        try {
            for(int i=0;i<100; i++) {
                ByteBuf buf = Unpooled.buffer();
                    if(i!=50) { 
                        buf = encode(getRecord(i));
                    } else {
                        buf = Unpooled.wrappedBuffer(new byte[] {3,4,3,4});
                    }
                    brds.sendData(buf);
            }
            brds.completeRequest().get();
        } catch (ExecutionException e) {
            t1 = e.getCause();
        }
        assertNotNull(t1);
        assertTrue(t1 instanceof YamcsApiException);
        RestExceptionMessage rem = ((YamcsApiException)t1).getRestExceptionMessage();
        
        assertTrue(rem.hasExtension(Table.rowsLoaded));
        int numRowsLoaded = rem.getExtension(Table.rowsLoaded);
        assertEquals(50, numRowsLoaded);
        verifyRecords("table2", 50);
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Row getRecord(int i) {
       
        Row tr = Row.newBuilder()
                .addColumn(ColumnInfo.newBuilder().setId(1).setName("a1").setType("INT").build()) //the column info is only required for the first record actually
                .addColumn(ColumnInfo.newBuilder().setId(2).setName("a2").setType("STRING").build())
                .addCell(Cell.newBuilder().setColumnId(1).setData(ByteString.copyFrom(csint.toByteArray(i))).build())
                .addCell(Cell.newBuilder().setColumnId(2).setData(ByteString.copyFrom(csstr.toByteArray("test "+i))).build())
                .build();
        return tr;
    }
    
    private void verifyRecords (String tblName, int n) throws Exception {
        List<TableRecord> trList = new ArrayList<>();
        CompletableFuture<Void> cf1 = restClient.doBulkGetRequest("/archive/IntegrationTest/downloads/tables/"+tblName, (data) -> {
            TableRecord tr;
            try {
                tr = TableRecord.parseFrom(data);
                trList.add(tr);
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
        });
        
        cf1.get();
        assertEquals(n, trList.size());
    }
    private void verifyRecordsDumpFormat (String tblName, int n) throws Exception {
        List<Row> trList = new ArrayList<>();
        CompletableFuture<Void> cf1 = restClient.doBulkGetRequest("/archive/IntegrationTest/downloads/tables/"+tblName+"?format=dump", (data) -> {
            Row tr;
            try {
                tr = Row.parseFrom(data);
                trList.add(tr);
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
        });
        
        cf1.get();
        assertEquals(n, trList.size());
    }
    
    private BulkRestDataSender initiateTableLoad(String tblName) throws Exception {        
        restClient.setSendMediaType(MediaType.PROTOBUF);
        restClient.setAcceptMediaType(MediaType.PROTOBUF);
        createTable(tblName);
        CompletableFuture<BulkRestDataSender> cf = restClient.doBulkSendRequest("/archive/IntegrationTest/tables/"+tblName+"/data", HttpMethod.POST);
        BulkRestDataSender brds = cf.get();
        
        return brds;
    }
    
    
    private void createTable(String tblName) throws Exception{
        YarchDatabase ydb = YarchDatabase.getInstance(yamcsInstance);
        TupleDefinition td = new TupleDefinition();
        td.addColumn("a1", DataType.INT);
        td.addColumn("a2", DataType.STRING);
        
        TableDefinition tblDef = new TableDefinition(tblName,  td, Arrays.asList("a1"));
        ydb.createTable(tblDef);
        
    }
    ByteBuf encode(MessageLite... msgl) throws IOException {
        ByteBuf buf = Unpooled.buffer();
        ByteBufOutputStream bufstream = new ByteBufOutputStream(buf);
        for(MessageLite msg: msgl) {
            msg.writeDelimitedTo(bufstream);
        }
        bufstream.close();
        return buf;
    }
}
