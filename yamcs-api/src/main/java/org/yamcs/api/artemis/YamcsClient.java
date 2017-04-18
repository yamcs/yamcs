package org.yamcs.api.artemis;

import java.io.IOException;
import java.util.UUID;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.yamcs.YamcsException;
import org.yamcs.api.YamcsApiException;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.StringMessage;

import com.google.protobuf.MessageLite;

import static org.yamcs.api.artemis.Protocol.*;

/**
 * Collect here scenarios and corresponding methods for sending/receiving data:
 *  sendRequest via rpcProducer 
 *     - "method" name as property and parameters as a ProtoBuf message
 *     - should automatically encode the replyTo address
 *  sendReply via rpcProducer
 *     - OK/NOK as property and return parameters as a ProtoBuf message
 *     - should somehow correlate with the request TODO
 *  
 *  executeRpc via rpcProducer/rpcConsumer
 *     -sendRequest and wait for replay a configurable timeout
 *  
 *  sendData via dataProducer
 *     - one chunk in a stream of data.
 *     - should manage end of stream conditions
 *     - should manage multiplexing different ProtoBuf message types
 *      
 *  sendEvent via dataProducer 
 *     - event name as property and parameters as ProtoBuf message
 *     
 *     
 * @author nm
 *
 */
public class YamcsClient {

    public final static String DATA_ADDRESS_PREFIX="tempDataAddress.";
    public final static String DATA_QUEUE_PREFIX="tempDataQueue.";

    public final static String RPC_ADDRESS_PREFIX="tempRpcAddress.";
    public final static String RPC_QUEUE_PREFIX="tempRpcQueue.";

    public ClientConsumer dataConsumer;
    public SimpleString dataQueue;
    public SimpleString dataAddress;


    public SimpleString rpcAddress; //this is my own address, messages are received via the consumer
    public SimpleString rpcQueue;
    public ClientConsumer rpcConsumer;
    public ClientProducer rpcProducer;

    private ClientProducer dataProducer;

    public long rpcTimeout=10000;
    long dataTimeout=10000;

    private final YamcsSession yamcsSession;


    YamcsClient(YamcsSession yamcsSession) {
        this.yamcsSession=yamcsSession;
    }

    public void sendErrorReply(SimpleString replyto, String message) throws YamcsApiException {
        synchronized(yamcsSession) {
            ClientMessage replyMsg = yamcsSession.session.createMessage(false);
            replyMsg.putStringProperty(MSG_TYPE_HEADER_NAME, "ERROR");
            replyMsg.putStringProperty(ERROR_MSG_HEADER_NAME, message);
           
            try {
                rpcProducer.send(replyto, replyMsg);
            } catch (ActiveMQException e1) {
                throw new YamcsApiException("Failed to send message to "+replyto, e1);
            }
            
        }
    }

    /**
     * Sends a ActiveMQ message containing an YamcsException.
     * The exception type and string message are encoded in the headers while the extra payload (if any) is encoded in the body
     * 
     * The exception extra arguments are encoded as 
     * @param replyto
     * @param e
     * @throws YamcsApiException
     */
    public void sendErrorReply(SimpleString replyto, YamcsException e) throws YamcsApiException {
        synchronized(yamcsSession) {
            ClientMessage replyMsg = yamcsSession.session.createMessage(false);
            replyMsg.putStringProperty(MSG_TYPE_HEADER_NAME, "ERROR");
            replyMsg.putStringProperty(ERROR_MSG_HEADER_NAME, e.getMessage());

            String type=e.getType();
            if(type!=null) {
                replyMsg.putStringProperty(ERROR_TYPE_HEADER_NAME, type);
            }

            byte[] body=e.getExtra();
            if(body!=null) {
                replyMsg.getBodyBuffer().writeBytes(body);;
            }

            try {
                rpcProducer.send(replyto, replyMsg);
            } catch (ActiveMQException e1) {
                throw new YamcsApiException("Failed to send message to "+replyto, e1);
            }
        }
    }

    public void sendReply(SimpleString replyto, String response, MessageLite body) throws YamcsApiException {
        synchronized(yamcsSession) {
            ClientMessage replyMsg = yamcsSession.session.createMessage(false);
            replyMsg.putStringProperty(MSG_TYPE_HEADER_NAME, response);
            if(body!=null) {
                encode(replyMsg, body);
            }
            try {
                rpcProducer.send(replyto, replyMsg);
            } catch (ActiveMQException e) {
                throw new YamcsApiException("Cannot send data to "+replyto, e);
            }
        }
    }



    public void sendRequest(SimpleString toAddress, String request, MessageLite body) throws YamcsApiException {
        synchronized(yamcsSession) {
            ClientMessage msg=yamcsSession.session.createMessage(false);
            msg.putStringProperty(REPLYTO_HEADER_NAME, rpcAddress);
            if(dataAddress!=null) 
                msg.putStringProperty(DATA_TO_HEADER_NAME, dataAddress);
            msg.putStringProperty(REQUEST_TYPE_HEADER_NAME, request);
            if(body!=null)  {
                encode(msg,body);
            }
            try {
                rpcProducer.send(toAddress, msg);
            } catch (ActiveMQException e) {
                throw new YamcsApiException("Cannot send data to "+toAddress, e);
            }
        }
    }


    public MessageLite executeRpc(SimpleString toAddress, String request, MessageLite body, MessageLite.Builder responseBuilder) throws YamcsApiException, YamcsException {
        synchronized(yamcsSession) {
            try {
                sendRequest(toAddress, request, body);
                ClientMessage msg = rpcConsumer.receive(rpcTimeout);
                if(msg==null) {
                    throw new YamcsApiException("did not receive a response to "+request+" in "+rpcTimeout+" milliseconds");
                }
                String resp=msg.getStringProperty(MSG_TYPE_HEADER_NAME);
                if("ERROR".equals(resp)) {
                    String type=null;
                    if(msg.containsProperty(ERROR_TYPE_HEADER_NAME)) {
                        type=msg.getStringProperty(ERROR_TYPE_HEADER_NAME);
                    }
                    String errormsg=msg.getStringProperty(ERROR_MSG_HEADER_NAME);
                    int size=msg.getBodySize();
                    byte[] extra=null;
                    if(size>0) {
                        extra=new byte[size];
                        msg.getBodyBuffer().readBytes(extra);
                    }
                    throw new YamcsException(type, errormsg, extra);
                }
                if(responseBuilder==null) {
                    return null;
                }
                else return decode(msg, responseBuilder);
            } catch (ActiveMQException e) {
                throw new YamcsApiException(e.getMessage(), e);
            }
        }
    }

    public void close() throws ActiveMQException {
        if(rpcProducer!=null) {
            rpcProducer.close();
        }
        if(rpcConsumer!=null) {
            rpcConsumer.close();
        }
        if(dataConsumer!=null) {
            dataConsumer.close();
        }
        if(dataProducer!=null) {
            dataProducer.close();
        }
    }

    public void sendDataError(SimpleString toAddress, String message) throws IOException, YamcsApiException {
        synchronized(yamcsSession) {
            sendData(toAddress, ProtoDataType.DT_ERROR, StringMessage.newBuilder().setMessage(message).build());
        }
    }

    public void sendData(SimpleString toAddress, org.yamcs.protobuf.Yamcs.ProtoDataType type, MessageLite data) throws YamcsApiException {
        synchronized(yamcsSession) {
            ClientMessage msg=yamcsSession.session.createMessage(false);
            msg.putIntProperty(DATA_TYPE_HEADER_NAME, type.getNumber());
            if(data!=null){
                encode(msg,data);
            }
            try {
                dataProducer.send(toAddress, msg);
            } catch (ActiveMQException e) {
                throw new YamcsApiException("Cannot send data to "+toAddress, e);
            }
        }
    }

    public void sendDataEnd(SimpleString toAddress) throws YamcsApiException {
        synchronized(yamcsSession) {
            ClientMessage msg=yamcsSession.session.createMessage(false);
            msg.putIntProperty(DATA_TYPE_HEADER_NAME, ProtoDataType.STATE_CHANGE.getNumber());
            try {
                dataProducer.send(toAddress,msg);
            } catch (ActiveMQException e) {
                throw new YamcsApiException("Failed to send data to "+toAddress, e);
            }
        }
    }
    /**
     * Receives and decodes data
     *  Returns null if the FINISH message is received
     */
    public MessageLite receiveData(MessageLite.Builder dataBuilder) throws YamcsException, YamcsApiException, ActiveMQException {
        ClientMessage msg = dataConsumer.receive(dataTimeout);
        if(msg==null) {
            throw new YamcsException("did not received a data message, timeout"+dataTimeout);
        }
        int dt=msg.getIntProperty(DATA_TYPE_HEADER_NAME);
        if(dt==ProtoDataType.STATE_CHANGE.getNumber()){
            return null;
        } else if(dt==ProtoDataType.DT_ERROR.getNumber()) {
            StringMessage errormsg=(StringMessage) decode(msg, StringMessage.newBuilder());
            throw new YamcsException(errormsg.getMessage());
        }
        return decode(msg,dataBuilder);
    }


    public MessageLite receiveImmediate(MessageLite.Builder dataBuilder) throws YamcsException, YamcsApiException {
        ClientMessage msg;
        try {
            msg = dataConsumer.receive(dataTimeout);
        } catch (ActiveMQException e) {
            throw new YamcsApiException("Exception receiving data", e);
        }
        if(msg==null) {
            return null;
        }
        int dt = msg.getIntProperty(DATA_TYPE_HEADER_NAME);
        if(dt==ProtoDataType.STATE_CHANGE.getNumber()){
            return null;
        } else if(dt==ProtoDataType.DT_ERROR.getNumber()) {
            StringMessage errormsg=(StringMessage) decode(msg, StringMessage.newBuilder());
            throw new YamcsException(errormsg.getMessage());
        }
        return decode(msg,dataBuilder);
    }


    /**
     * sends an event via the dataProducer
     * @param toAddress
     * @param eventName name of the event to be encoded in the {@value Protocol#HDR_EVENT_NAME} header
     * @param data
     * @throws ActiveMQException
     */
    public void sendEvent(SimpleString toAddress, String eventName, MessageLite data) throws ActiveMQException {
        synchronized(yamcsSession) {
            ClientMessage msg=yamcsSession.session.createMessage(false);
            msg.putStringProperty(HDR_EVENT_NAME, eventName);
            if(data!=null){
                encode(msg,data);
            }
            dataProducer.send(toAddress, msg);
        }
    }


    public YamcsSession getYamcsSession() {
        return yamcsSession;
    }


    public static class ClientBuilder {
        boolean invm=true;

        boolean dataProducer=false;
        boolean dataConsumer=false;
        boolean rpc=false;


        SimpleString rpcAddress;
        SimpleString rpcQueue;

        SimpleString dataAddress;
        SimpleString dataQueue;

        SimpleString filter=null;

        final YamcsSession yamcsSession;

        private boolean browseOnly=false;

        public ClientBuilder(YamcsSession yamcsSession) {
            if(yamcsSession.session==null) {
                throw new IllegalArgumentException();
            }
            this.yamcsSession=yamcsSession;
        }

        /**
         * mark this as a rpc client
         * @param rpc
         * @return
         */
        public ClientBuilder setRpc(boolean rpc) {
            this.rpc=rpc;
            return this;
        }

        /**
         * mark this as a rpc server and set the rpc address
         * @param address
         * @return
         */
        public ClientBuilder setRpcAddress(SimpleString address) {
            this.rpc=true;
            this.rpcAddress=address;
            return this;
        }

        /**
         * Create a data producer. The address to send to is specified when sending each message
         * @param b
         * @return
         */
        public ClientBuilder setDataProducer(boolean b) {
            dataProducer=b;
            return this;
        }

        /**
         * Create a data consumer from the specified address and queue. 
         *  Both can be null in which case a temporary address and/or a temporary queue are created,
         * @param address
         * @param queue
         * @return
         */
        public ClientBuilder setDataConsumer(SimpleString address, SimpleString queue) {
            this.dataConsumer=true;
            this.dataAddress = address;
            this.dataQueue = queue;
            return this;
        }

        /**
         * Sets a filter for the data consumer
         * @param filter
         * @return
         */
        public ClientBuilder setFilter(SimpleString filter) {
            this.filter=filter;
            return this;
        }
        /**
         * Sets the data consumer browseOnly property
         * @param b
         * @return
         */
        public ClientBuilder setBrowseOnly(boolean b) {
            this.browseOnly=b;
            return this;
        }

        public YamcsClient build() throws YamcsApiException {
            try {
                YamcsClient c=new YamcsClient(yamcsSession);
                if(rpc) {
                    c.rpcAddress = (rpcAddress==null)?new SimpleString(RPC_ADDRESS_PREFIX + UUID.randomUUID().toString()):rpcAddress;
                    c.rpcQueue = (rpcQueue==null)?new SimpleString(RPC_QUEUE_PREFIX + UUID.randomUUID().toString()):rpcQueue;
                    createAddressAndQueue(yamcsSession.session, c.rpcAddress, c.rpcQueue, filter);
                    c.rpcConsumer = yamcsSession.session.createConsumer(c.rpcQueue);
                    c.rpcProducer = yamcsSession.session.createProducer();
                } 

                if(dataConsumer) {
                    c.dataAddress = (dataAddress==null)?new SimpleString(DATA_ADDRESS_PREFIX + UUID.randomUUID().toString()):dataAddress;
                    c.dataQueue = (dataQueue==null)?new SimpleString(DATA_QUEUE_PREFIX + UUID.randomUUID().toString()):dataQueue;
                    createAddressAndQueue(yamcsSession.session, c.dataAddress, c.dataQueue, filter);
                    c.dataConsumer = yamcsSession.session.createConsumer(c.dataQueue, browseOnly);
                }

                if(dataProducer) {
                    c.dataProducer = yamcsSession.session.createProducer();
                }

                return c;
            } catch (ActiveMQException e) {
                throw new YamcsApiException("cannot create yamcs client: "+e.getMessage(), e);
            }

        }

        static void createAddressAndQueue(ClientSession session, SimpleString a, SimpleString q, SimpleString filter) throws ActiveMQException {
            if(!session.queueQuery(q).isExists()) {
                if(filter==null) {
                    session.createTemporaryQueue(a, q);
                } else {
                    session.createTemporaryQueue(a, q, filter);
                }
            }
        }


    }

    /**
     * Send message to the address using the data producer.
     *      
     * @param hornetAddress
     * @param msg
     * @throws YamcsApiException
     */
    public synchronized void sendData(SimpleString hornetAddress, ClientMessage msg) throws YamcsApiException {
        synchronized(yamcsSession) {
            try {
                dataProducer.send(hornetAddress, msg);
            } catch (ActiveMQException e) {
               throw new YamcsApiException("Failed to send message to "+hornetAddress, e);
            }
        }
    }

    public ClientProducer getDataProducer() {
        return dataProducer;
    }
}
