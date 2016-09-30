package org.yamcs.api.artemis;


import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.utils.ActiveMQBufferInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.YamcsApiException;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.utils.ActiveMQBufferOutputStream;

import com.google.protobuf.MessageLite;

public class Protocol {
    static Logger log=LoggerFactory.getLogger(Protocol.class.getName());

    private static ProducerKiller killer;
    public static SimpleString getReplayControlAddress(String instance) {
        return new SimpleString(instance+".replayControl");
    }
    
    public static SimpleString getYarchRetrievalControlAddress(String instance) {
        return new SimpleString(instance+".retrievalControl");
    }

    public static SimpleString getYarchIndexControlAddress(String instance) {
        return new SimpleString(instance+".indexControl");
    }

    public static SimpleString getEventRealtimeAddress(String instance) {
        return new SimpleString(instance+".events_realtime");
    }

    public static SimpleString getParameterRealtimeAddress(String instance) {
        return new SimpleString(instance+".parameters_realtime");
    }

    public static SimpleString getPacketRealtimeAddress(String instance) {
        return new SimpleString(instance+".tm_realtime");
    }

    public static SimpleString getPacketAddress(String instance, String streamName) {
        return new SimpleString(instance+"."+streamName);
    }

    final static public String MSG_TYPE_HEADER_NAME="yamcs-content";

    final static public String REQUEST_TYPE_HEADER_NAME="yamcs-req-type";

    final static public String ERROR_MSG_HEADER_NAME="yamcs-error-msg";
    final static public String ERROR_TYPE_HEADER_NAME="yamcs-error-type";


    final static public SimpleString DATA_TO_HEADER_NAME=new SimpleString("yamcs-data-to");
    final static public SimpleString YAMCS_SERVER_CONTROL_ADDRESS=new SimpleString("yamcsControl");
    final static public SimpleString REPLYTO_HEADER_NAME=new SimpleString("yamcs-reply-to");

    final public static String IN_VM_FACTORY = "org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory";

    /**
     * used to send chunks of data in a stream
     */
    final static public String DATA_TYPE_HEADER_NAME="dt";

    /**
     * used to send one shot events (publish/subscribe) 
     */
    final static public String HDR_EVENT_NAME="en";

    /**
     * address where all the I/O Links are registered (e.g. tmprovide and tcuplinker)
     * there is also a queue with the same name that can be browsed to get the initial list
     */
    final static public SimpleString LINK_INFO_ADDRESS=new SimpleString("linkInfo");
    /**
     * address where link control messages can be sent
     */
    final static public SimpleString LINK_CONTROL_ADDRESS=new SimpleString("linkControl");

    /**
     * address where all the Channels are registered
     * there is also a queue with the same name that can be browsed to get the initial list
     */
    final static public SimpleString YPROCESSOR_INFO_ADDRESS=new SimpleString("channelInfo");

    /**
     * address where channel control commands can be sent
     */
    final static public SimpleString YPROCESSOR_CONTROL_ADDRESS=new SimpleString("channelControl");

    /**
     * address where channel statistics are published regularly
     */
    final static public SimpleString YPROCESSOR_STATISTICS_ADDRESS=new SimpleString("channelStatistics");

    /**
     * address where all the Command Queues and command stationed in the Queues are registered
     * there is also a queue with the same name that can be browsed to get the initial list
     */
    final static public SimpleString CMDQUEUE_INFO_ADDRESS=new SimpleString("cmdQueueInfo");

    /**
     * address where command queue control commands can be sent
     */
    final static public SimpleString CMDQUEUE_CONTROL_ADDRESS=new SimpleString("cmdQueueControl");


    public static MessageLite decode(ClientMessage msg, MessageLite.Builder builder) throws YamcsApiException {
        try {
            ActiveMQBuffer buf = msg.getBodyBuffer();
            return builder.mergeFrom(new ActiveMQBufferInputStream(buf)).build();
        } catch(IOException e) {
            throw new YamcsApiException(e.getMessage(), e);
        }
    }

    public static MessageLite.Builder decodeBuilder(ClientMessage msg, MessageLite.Builder builder) throws YamcsApiException {
        try {
            ActiveMQBuffer buf = msg.getBodyBuffer();
            return builder.mergeFrom(new ActiveMQBufferInputStream(buf));
        } catch(IOException e) {
            throw new YamcsApiException(e.getMessage(), e);
        }
    }

    public static void encode(ClientMessage msg, MessageLite ml){
        try {
            ml.writeTo(new ActiveMQBufferOutputStream(msg.getBodyBuffer()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean endOfStream(ClientMessage msg) {
        int type=msg.getIntProperty(DATA_TYPE_HEADER_NAME);
        return (type==ProtoDataType.STATE_CHANGE.getNumber());
    }

    /**
     * Closes producer when a consumer for address a has been detected as dead
     * @param p
     * @param a
     */
    public static synchronized void killProducerOnConsumerClosed(ClientProducer p, SimpleString a) {
        if((killer==null) || killer.session.isClosed()) {//killer.session gets closed when the artemis is stopped (during test execution by maven)
            try {
                killer = new ProducerKiller();
            } catch (Exception e) {
                log.error("Could not create ProducerKiller", e);
            }
        }
        if(killer!=null) killer.add(p,a);
    }

    /**
     * this is supposed to close the data producers that are stuck writing into queues 
     * for which there is no consumer (e.g. when the consumer loses the network connection to the server) 
     *
     */
    static class ProducerKiller implements MessageHandler {
        ClientSession session;
        Map<SimpleString, ClientProducer> producers=new ConcurrentHashMap<SimpleString, ClientProducer>();
        ServerLocator locator;

        ProducerKiller() throws Exception {
            locator = ActiveMQClient.createServerLocatorWithoutHA(new TransportConfiguration(IN_VM_FACTORY));
            ClientSessionFactory factory =  locator.createSessionFactory();

            session = factory.createSession(YamcsSession.hornetqInvmUser, YamcsSession.hornetqInvmPass, false, true, true, true, 1);

            //          session.createQueue("example", "example", true);
            session.createTemporaryQueue("activemq.notifications","ProducerKiller");
            ClientConsumer consumer = session.createConsumer("ProducerKiller");
            consumer.setMessageHandler(this);
            session.start();
        }
        public void add(ClientProducer p, SimpleString a) {
            producers.put(a,p);
        }
        @Override
        public void onMessage(ClientMessage msg) {
            System.out.println("msg: "+msg);
            String amq_notifType = msg.getStringProperty("_AMQ_NotifType");

            if("CONSUMER_CLOSED".equals(amq_notifType)){
                SimpleString amq_address=msg.getSimpleStringProperty("_AMQ_Address");
                ClientProducer p=producers.remove(amq_address);
                if(p!=null && !p.isClosed()) {
                    try {
                        log.warn("closing producer {} because the consumer to the address {} has closed",p, amq_address);
                        p.close();
                    } catch (ActiveMQException e) {
                        e.printStackTrace();
                    }
                }
            }

        }
        public void close() {
            if(locator!=null) {
                locator.close();
            }
        }
    }
    /**
     * Close killer (used in order to reduce warnings during maven builds
     */
    public static synchronized void closeKiller() {
        if(killer!=null) killer.close();
    }
}
