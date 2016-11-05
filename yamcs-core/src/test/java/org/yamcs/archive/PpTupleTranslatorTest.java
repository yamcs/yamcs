package org.yamcs.archive;

import static org.junit.Assert.*;
import static org.yamcs.api.artemis.Protocol.DATA_TYPE_HEADER_NAME;
import static org.yamcs.api.artemis.Protocol.decode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.tctm.PpProviderAdapter;
import org.yamcs.StreamInitializer;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.api.artemis.YamcsClient;
import org.yamcs.api.artemis.YamcsSession;
import org.yamcs.api.artemis.YamcsClient.ClientBuilder;
import org.yamcs.hornetq.ArtemisManagement;
import org.yamcs.hornetq.ArtemisServer;
import org.yamcs.hornetq.PpTupleTranslator;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Pvalue.ParameterData.Builder;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchTestCase;
import org.yamcs.hornetq.StreamAdapter;

public class PpTupleTranslatorTest extends YarchTestCase {
    static EmbeddedActiveMQ artemisServer;
    public static int sequenceCount = 0;
    public static final String COL_BYTE = "/pp/byte";
    public static final String COL_STR = "/pp/string";
    public static final String COL_DOUBLE = "/pp/double";
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        artemisServer = ArtemisServer.setupArtemis();
        ArtemisManagement.setupYamcsServerControl();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        artemisServer.stop();
    }

    /**
     * Gets a sample message containing three simulated processed parameters
     * and timestamped for now, with a continually incrementing sequence
     * number.
     * 
     * @param ys
     * @return
     */
    public static ClientMessage getMessage( YamcsSession ys ) {
        Builder b = ParameterData.newBuilder();
        b.addParameter( 
                ParameterValue.newBuilder().setEngValue( 
                        Value.newBuilder().setType( Type.SINT32 ).setSint32Value( 1 ).build()
                        ).setId( NamedObjectId.newBuilder().setName( COL_BYTE ).build() )
                );
        b.addParameter( 
                ParameterValue.newBuilder().setEngValue(
                        Value.newBuilder().setType( Type.STRING ).setStringValue( "test" ).build()
                        ).setId( NamedObjectId.newBuilder().setName( COL_STR ).build() )
                );
        b.addParameter( 
                ParameterValue.newBuilder().setEngValue( 
                        Value.newBuilder().setType( Type.DOUBLE ).setDoubleValue( 1.234 ).build()
                        ).setId( NamedObjectId.newBuilder().setName( COL_DOUBLE ).build() )
                );

        ClientMessage msg = ys.session.createMessage( false );
        Protocol.encode( msg, b.build() );

        msg.putIntProperty( DATA_TYPE_HEADER_NAME, ProtoDataType.PP.getNumber() );

        long curTime = TimeEncoding.getWallclockTime();
        msg.putLongProperty( PpProviderAdapter.PP_TUPLE_COL_GENTIME, curTime - 10 );
        msg.putStringProperty( PpProviderAdapter.PP_TUPLE_COL_PPGROUP, "no-group" );
        msg.putIntProperty( PpProviderAdapter.PP_TUPLE_COL_SEQ_NUM, PpTupleTranslatorTest.sequenceCount ++ );
        msg.putLongProperty( PpProviderAdapter.PP_TUPLE_COL_RECTIME, curTime );

        return msg;
    }

    public static Tuple getTuple() {
        TupleDefinition tupleDef = PpProviderAdapter.PP_TUPLE_DEFINITION.copy();
        tupleDef.addColumn( COL_BYTE, DataType.BYTE );
        tupleDef.addColumn( COL_STR, DataType.STRING );
        tupleDef.addColumn( COL_DOUBLE, DataType.DOUBLE );

        List<Object> cols=new ArrayList<Object>(4);
        cols.add( TimeEncoding.getWallclockTime() - 10 );
        cols.add( "no-group" );
        cols.add( PpTupleTranslatorTest.sequenceCount ++ );
        cols.add( TimeEncoding.getWallclockTime() );

        cols.add( 1 ); // byte
        cols.add( "test" ); // string
        cols.add( 1.234 ); // float

        return new Tuple(tupleDef, cols);
    }

    @Test
    public void testTranslation() throws Exception {
        StreamInitializer streamInit = new StreamInitializer(context.getDbName());
        streamInit.createStreams();
        
        PpRecorder ppRecorder = new PpRecorder(context.getDbName());
        ppRecorder.startAsync();

        // Get the stream
        Stream rtstream = ydb.getStream( "pp_realtime" );
        assertNotNull(rtstream);

        // Add the adapter under test
        SimpleString address = new SimpleString("pp_realtime");
        StreamAdapter streamAdapter = new StreamAdapter( rtstream, address, new PpTupleTranslator() );

        // Create a client to generate messages with
        YamcsSession ys = YamcsSession.newBuilder().build();
        ClientBuilder cb = ys.newClientBuilder();
        cb.setDataProducer( true ); // We produce data
        cb.setDataConsumer( address,null ); // This is the destination for the produced data
        YamcsClient msgClient = cb.build();
        final AtomicInteger hornetReceivedCounter=new AtomicInteger(0);
        // 
        msgClient.dataConsumer.setMessageHandler (
                new MessageHandler() {
                    @Override
                    public void onMessage(ClientMessage msg) {
                        try {
                            ParameterData pd = (ParameterData)decode( msg, ParameterData.newBuilder() );
                            assertEquals( 3, pd.getParameterCount() );
                            // Count received messages
                            hornetReceivedCounter.getAndIncrement();
                        } catch (YamcsApiException e) {
                            fail("Exception received: "+e);
                        }
                    }
                } );

        // Now send some messages
        final int numMessages = 100;
        for( int i=0; i<numMessages; i++ ) {
            msgClient.sendData( address, getMessage( ys ) );
        }		
        Thread.sleep( 3000 );

        // And make sure the messages have appeared in the table
        final AtomicInteger tableReceivedCounter=new AtomicInteger(0);
        execute("create stream stream_pp_out as select * from "+PpRecorder.TABLE_NAME);
        Stream s=ydb.getStream("stream_pp_out");
        final Semaphore finished=new Semaphore(0);
        s.addSubscriber(new StreamSubscriber() {
            @Override
            public void streamClosed(Stream stream) { }
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                ParameterValue pv = (ParameterValue)tuple.getColumn( COL_STR );
                assertTrue( "test".equals( pv.getEngValue().getStringValue() ) );

                pv = (ParameterValue)tuple.getColumn( COL_DOUBLE );
                assertEquals( 1.234, pv.getEngValue().getDoubleValue(), 0.0001 );

                pv = (ParameterValue)tuple.getColumn( COL_BYTE );
                assertEquals( 1, pv.getEngValue().getSint32Value() );

                assertTrue( "no-group".equals( tuple.getColumn( PpProviderAdapter.PP_TUPLE_COL_PPGROUP ) ) );

                assertEquals( tableReceivedCounter.get(), tuple.getColumn( PpProviderAdapter.PP_TUPLE_COL_SEQ_NUM ) );
                tableReceivedCounter.incrementAndGet();

                long gentime = ((Long)tuple.getColumn( PpProviderAdapter.PP_TUPLE_COL_GENTIME )).longValue();
                long rectime = ((Long)tuple.getColumn( PpProviderAdapter.PP_TUPLE_COL_RECTIME )).longValue();
                assertEquals( gentime, rectime - 10, 0.0001 );

                if(tableReceivedCounter.get()==numMessages)finished.release();
            }
        });
        s.start();
        finished.tryAcquire(10, TimeUnit.SECONDS);

        assertEquals(numMessages, hornetReceivedCounter.get());
        assertEquals(numMessages, tableReceivedCounter.get());
        streamAdapter.quit();

        ppRecorder.stopAsync();
        msgClient.close();
        ys.close();
    }
}
