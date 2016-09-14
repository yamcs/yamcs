package org.yamcs.api.rest;


import io.netty.handler.codec.http.HttpMethod;

import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.yamcs.api.YamcsApiException;
import org.yamcs.api.ws.YamcsConnectionProperties;
import org.yamcs.protobuf.Rest.ListInstancesResponse;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;


/**
 * A simple Yamcs Rest client to help with basic requests.
 * 
 * @author nm
 *
 */
public class RestClient {
    final YamcsConnectionProperties connectionProperties;
    long timeout = 5000; //timeout in milliseconds

    final HttpClient httpClient;

 
    /** maximum size of the responses - this is not applicable to bulk requests */
    final static long MAX_RESPONSE_LENGTH = 1024*1024;

    /**max message length of an individual ProtoBuf message part of a bulk retrieval*/ 
    final static int MAX_MESSAGE_LENGTH = 1024*1024;


    public RestClient(YamcsConnectionProperties connectionProperties) {
        this.connectionProperties = connectionProperties;
        httpClient = new HttpClient(timeout, true);
        httpClient.setMaxResponseLength(MAX_RESPONSE_LENGTH);
    }
    /**
     * Retrieve the list of yamcs instances from the server. The operation will block until the list is received.
     * 
     * @return the list of yamcs instances configured on the server
     * @throws Exception 
     */
    public List<YamcsInstance> blockingGetYamcsInstances() throws Exception {
        return getYamcsInstances().get();
    }

    public CompletableFuture<List<YamcsInstance>> getYamcsInstances() {
        CompletableFuture<byte[]> future = doRequest("/instances",HttpMethod.GET);
        return future.thenApply(b -> {
            try {
                return ListInstancesResponse.parseFrom(b).getInstanceList();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
    }
    
    /**
     * Performs a request with an empty body.
     * See {@link #doRequest(String, HttpMethod, String) getComponentAt} 
     * @param resource
     * @param method
     * @return a the response body
     */
    public CompletableFuture<byte[]> doRequest(String resource, HttpMethod method) {
        return doRequest(resource, method, "");
    }

    /**
     * Perform asynchronously the request indicated by the HTTP method and return the result as a future providing byte array. 
     * 
     * Note that the response body will be limited to {@value #MAX_RESPONSE_LENGTH} - in case the server sends more than that, 
     * the CompletableFuture will completed with an error (the get() method will throw an Exception); the partial response will not be available. 
     * 
     * @param resource - the url and query parameters after the "/api" part.
     * @param method - http method to use
     * @param body - the body of the request. Can be used even for the GET requests although stricly not allowed by the HTTP standard.
     * @return - the response body
     * @throws RuntimeException(URISyntaxException) thrown in case the resource specification is invalid
     */
    public CompletableFuture<byte[]> doRequest(String resource, HttpMethod method, String body) {
        CompletableFuture<byte[]> cf;
        try {
            cf = httpClient.doAsyncRequest(connectionProperties.getRestApiUrl()+resource, method, body.getBytes(), false, connectionProperties.getAuthenticationToken());
        } catch (URISyntaxException e) { //throw a RuntimeException instead since if the code is not buggy it's unlikely to have this exception thrown
            throw new RuntimeException(e);
        }
        return cf;
    }

    /**
     * Perform asynchronously the request indicated by the HTTP method and return the result as a future providing byte array.
     * 
     * The "Content-Type" http header is set to "application/protobuf" so the body has to be protobuf encoded data
     * The "Accept" HTTP header is also set to "application/protobuf" so to return expected is also protobuf data.
     * 
     * @param resource
     * @param method
     * @param body protobuf encoded data.
     * @return future containing protobuf encoded data
     */
    public CompletableFuture<byte[]> doProtoBufRequest(String resource, HttpMethod method, byte[] body) {
        CompletableFuture<byte[]> cf;
        try {
            cf = httpClient.doAsyncRequest(connectionProperties.getRestApiUrl()+resource, method, body, true, connectionProperties.getAuthenticationToken());
        } catch (URISyntaxException e) { //throw a RuntimeException instead since if the code is not buggy it's unlikely to have this exception thrown
            throw new RuntimeException(e);
        }
        return cf;
    }
    /**
     * Performs a bulk request and provides the result piece by piece to the receiver.
     * 
     * The potentially large result is split into messages based on the VarInt size preceding each message. 
     * The maximum size of each individual message is limited to {@value #MAX_MESSAGE_LENGTH}
     * 
     * @param resource
     * @param receiver
     * @return future that is completed when the request is finished
     * @throws InterruptedException
     * @throws RuntimeException(URISyntaxException) - thrown if the uri + resource does not form a correct URL
     */
    public CompletableFuture<Void> doBulkGetRequest(String resource, BulkRestDataReceiver receiver) {
        MessageSplitter splitter = new MessageSplitter(receiver);
        try {
            return httpClient.doBulkRequest(connectionProperties.getRestApiUrl()+resource, HttpMethod.GET, "", connectionProperties.getAuthenticationToken(), splitter);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    static class MessageSplitter implements BulkRestDataReceiver {
        BulkRestDataReceiver finalReceiver;
        byte[] buffer = new byte[2*MAX_MESSAGE_LENGTH];
        int readOffset = 0;
        int writeOffset = 0;

        MessageSplitter(BulkRestDataReceiver finalReceiver) {
            this.finalReceiver = finalReceiver;
        }

        @Override
        public void receiveData(byte[] data) throws YamcsApiException {
            if(data.length>MAX_MESSAGE_LENGTH) {
                throw new YamcsApiException("Message too long: received "+data.length+" max length: "+MAX_MESSAGE_LENGTH);
            }
            System.out.println("--------receiving message readOffset: "+readOffset+" writeOffset: "+writeOffset+" data.length: "+data.length);
            
            int length = (data.length < buffer.length-writeOffset) ? data.length:buffer.length-writeOffset; 
            System.arraycopy(data, 0, buffer, writeOffset, length);
            writeOffset+=length;
            ByteBuffer bb = ByteBuffer.wrap(buffer);

            while(readOffset<writeOffset) {
                bb.position(readOffset);
                int msgLength = readVarInt32(bb);
                if(msgLength>MAX_MESSAGE_LENGTH) throw new YamcsApiException("Message too long: decodedMessagLength: "+msgLength+" max length: "+MAX_MESSAGE_LENGTH);
                if(msgLength > writeOffset-readOffset) break; 
     
                System.out.println("receiving message of length "+msgLength +" readOffset: "+readOffset+" writeOffset: "+writeOffset+" data.length: "+data.length);
                readOffset = bb.position();
                byte[] b = new byte[msgLength];
                System.arraycopy(buffer, readOffset, b, 0, msgLength);
                readOffset+=msgLength;
                System.out.println("sent data to receiver "+b.length);
                finalReceiver.receiveData(b);
            }
            System.arraycopy(buffer, readOffset, buffer, 0, writeOffset-readOffset);
            writeOffset-=readOffset;
            readOffset=0;
            if(length<data.length) {
                System.arraycopy(buffer, writeOffset, data, length, data.length-length);
                writeOffset+=(data.length-length);
            }
        }

        @Override
        public void receiveException(Throwable t) {
            finalReceiver.receiveException(t);
        }

    }

    public static int readVarInt32(ByteBuffer bb) throws YamcsApiException {
        byte b = bb.get();
        int v = b &0x7F;
        for (int shift = 7; (b & 0x80) != 0; shift += 7) {
            if(shift>28) throw new YamcsApiException("Invalid VarInt32: more than 5 bytes!");
            
            if(!bb.hasRemaining()) return Integer.MAX_VALUE;//we miss some bytes from the size itself
            b = bb.get();
            v |= (b & 0x7F) << shift;

        }
        return v;
    }
    
}
