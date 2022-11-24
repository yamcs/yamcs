package org.yamcs.client.base;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.yamcs.client.ClientException;
import org.yamcs.client.Credentials;

import com.google.protobuf.Message;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.cookie.Cookie;

/**
 * A simple Yamcs Rest client to help with basic requests.
 * 
 * @author nm
 *
 */
public class RestClient {

    private ServerURL serverURL;
    private final HttpClient httpClient;

    /** maximum size of the responses - this is not applicable to bulk requests */
    private final static int MAX_RESPONSE_LENGTH = 10 * 1024 * 1024;

    /** max message length of an individual ProtoBuf message part of a bulk retrieval */
    private final static int MAX_MESSAGE_LENGTH = 10 * 1024 * 1024;

    private boolean autoclose = true;

    /**
     * Creates a rest client that communications using protobuf
     */
    public RestClient(ServerURL serverURL) {
        this.serverURL = serverURL;
        httpClient = new HttpClient();
        httpClient.setMaxResponseLength(MAX_RESPONSE_LENGTH);
        httpClient.setAcceptMediaType(HttpClient.MT_PROTOBUF);
        httpClient.setSendMediaType(HttpClient.MT_PROTOBUF);
    }

    public synchronized void login(String username, char[] password) throws ClientException {
        String tokenUrl = serverURL + "/auth/token";
        httpClient.login(tokenUrl, username, password);
    }

    public synchronized void loginWithAuthorizationCode(String authorizationCode) throws ClientException {
        String tokenUrl = serverURL + "/auth/token";
        httpClient.loginWithAuthorizationCode(tokenUrl, authorizationCode);
    }

    public synchronized String authorizeKerberos(SpnegoInfo info) throws ClientException {
        return httpClient.authorizeKerberos(info);
    }

    /**
     * Performs a request with an empty body. Works using protobuf
     * 
     * @param resource
     * @param method
     * @return a the response body
     */
    public CompletableFuture<byte[]> doRequest(String resource, HttpMethod method) {
        return doRequest(resource, method, new byte[0]);
    }

    /**
     * Perform asynchronously the request indicated by the HTTP method and return the result as a future providing byte
     * array.
     * 
     * Note that the response body will be limited to {@value #MAX_RESPONSE_LENGTH} - in case the server sends more than
     * that, the CompletableFuture will completed with an error (the get() method will throw an Exception); the partial
     * response will not be available.
     * 
     * @param resource
     *            the url and query parameters after the "/api" part.
     * @param method
     *            http method to use
     * @param body
     *            the body of the request. Can be used even for the GET requests although strictly not allowed by the
     *            HTTP standard.
     * @return the response body
     * @throws IllegalArgumentException
     *             when the resource specification is invalid
     */
    public CompletableFuture<String> doRequest(String resource, HttpMethod method, String body) {
        CompletableFuture<byte[]> cf;
        try {
            cf = httpClient.doAsyncRequest(serverURL + "/api" + resource, method, body.getBytes());
        } catch (ClientException | IOException | GeneralSecurityException e) {
            // throw a RuntimeException instead since if the code is not buggy it's
            // unlikely to have this exception thrown
            throw new RuntimeException(e);
        }

        if (autoclose) {
            cf.whenComplete((v, t) -> {
                close();
            });
        }
        return cf.thenApply(b -> {
            return new String(b);
        });
    }

    /**
     * Perform asynchronously the request indicated by the HTTP method and return the result as a future providing byte
     * array.
     * 
     * To be used when performing protobuf requests.
     * 
     * @param resource
     * @param method
     * @param message
     * @return future containing protobuf encoded data
     */
    public CompletableFuture<byte[]> doRequest(String resource, HttpMethod method, Message message) {
        return doBaseRequest("/api" + resource, method, message.toByteArray());
    }

    /**
     * Perform asynchronously the request indicated by the HTTP method and return the result as a future providing byte
     * array.
     * 
     * To be used when performing protobuf requests.
     * 
     * @param resource
     * @param method
     * @param body
     *            protobuf encoded data.
     * @return future containing protobuf encoded data
     */
    public CompletableFuture<byte[]> doRequest(String resource, HttpMethod method, byte[] body) {
        return doBaseRequest("/api" + resource, method, body);
    }

    /**
     * Perform asynchronously the request indicated by the HTTP method and return the result as a future providing byte
     * array.
     * 
     * To be used when performing protobuf requests.
     * 
     * @param resource
     * @param method
     * @param body
     *            protobuf encoded data.
     * @return future containing protobuf encoded data
     */
    public CompletableFuture<byte[]> doBaseRequest(String resource, HttpMethod method, byte[] body) {
        CompletableFuture<byte[]> cf;
        try {
            cf = httpClient.doAsyncRequest(serverURL + resource, method, body);
        } catch (ClientException | IOException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
        if (autoclose) {
            cf.whenComplete((v, t) -> {
                close();
            });
        }

        return cf;
    }

    public CompletableFuture<Void> doBulkRequest(HttpMethod method, String resource, BulkRestDataReceiver receiver) {
        return doBulkRequest(method, resource, new byte[0], receiver);
    }

    /**
     * Performs a bulk request and provides the result piece by piece to the receiver.
     * 
     * The potentially large result is split into messages based on the VarInt size preceding each message. The maximum
     * size of each individual message is limited to {@value #MAX_MESSAGE_LENGTH}
     * 
     * @param method
     * @param resource
     * @param receiver
     * @return future that is completed when the request is finished
     * @throws RuntimeException
     *             if the uri + resource does not form a correct URL
     */
    public CompletableFuture<Void> doBulkRequest(HttpMethod method, String resource, byte[] body,
            BulkRestDataReceiver receiver) {
        CompletableFuture<Void> cf;
        MessageSplitter splitter = new MessageSplitter(receiver);
        try {
            cf = httpClient.doBulkReceiveRequest(serverURL + "/api" + resource, method,
                    body, splitter);
        } catch (ClientException | IOException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
        if (autoclose) {
            cf.whenComplete((v, t) -> {
                close();
            });
        }
        return cf;
    }

    private static class MessageSplitter implements BulkRestDataReceiver {
        BulkRestDataReceiver finalReceiver;
        byte[] buffer = new byte[2 * MAX_MESSAGE_LENGTH];
        int readOffset = 0;
        int writeOffset = 0;

        MessageSplitter(BulkRestDataReceiver finalReceiver) {
            this.finalReceiver = finalReceiver;
        }

        @Override
        public void receiveData(byte[] data) throws ClientException {
            if (data.length > MAX_MESSAGE_LENGTH) {
                throw new ClientException(
                        "Message too long: received " + data.length + " max length: " + MAX_MESSAGE_LENGTH);
            }

            int length = (data.length < buffer.length - writeOffset) ? data.length : buffer.length - writeOffset;
            System.arraycopy(data, 0, buffer, writeOffset, length);
            writeOffset += length;
            ByteBuffer bb = ByteBuffer.wrap(buffer);

            while (readOffset + 5 < writeOffset) {
                bb.position(readOffset);
                int msgLength = readVarInt32(bb);
                if (msgLength > MAX_MESSAGE_LENGTH) {
                    throw new ClientException("Message too long: decodedMessageLength: " + msgLength + " max length: "
                            + MAX_MESSAGE_LENGTH);
                }
                if (msgLength > writeOffset - bb.position()) {
                    break;
                }

                readOffset = bb.position();
                byte[] b = new byte[msgLength];
                System.arraycopy(buffer, readOffset, b, 0, msgLength);
                readOffset += msgLength;
                finalReceiver.receiveData(b);
            }

            System.arraycopy(buffer, readOffset, buffer, 0, writeOffset - readOffset);
            writeOffset -= readOffset;
            readOffset = 0;
            if (length < data.length) {
                System.arraycopy(buffer, writeOffset, data, length, data.length - length);
                writeOffset += (data.length - length);
            }
        }

        @Override
        public void receiveException(Throwable t) {
            finalReceiver.receiveException(t);
        }
    }

    public static int readVarInt32(ByteBuffer bb) throws ClientException {
        byte b = bb.get();
        int v = b & 0x7F;
        for (int shift = 7; (b & 0x80) != 0; shift += 7) {
            if (shift > 28) {
                throw new ClientException("Invalid VarInt32: more than 5 bytes!");
            }

            if (!bb.hasRemaining()) {
                return Integer.MAX_VALUE;// we miss some bytes from the size itself
            }
            b = bb.get();
            v |= (b & 0x7F) << shift;

        }
        return v;
    }

    public void setSendMediaType(String sendMediaType) {
        httpClient.setSendMediaType(sendMediaType);
    }

    public void setAcceptMediaType(String acceptMediaType) {
        httpClient.setAcceptMediaType(acceptMediaType);
    }

    public void setMaxResponseLength(int size) {
        httpClient.setMaxResponseLength(size);
    }

    public void setUserAgent(String userAgent) {
        httpClient.setUserAgent(userAgent);
    }

    public void close() {
        httpClient.close();
    }

    public boolean isAutoclose() {
        return autoclose;
    }

    public Credentials getCredentials() {
        return httpClient.getCredentials();
    }

    public void setCredentials(Credentials credentials) {
        httpClient.setCredentials(credentials);
    }

    /**
     * if autoclose is set, the httpClient will be automatically closed at the end of the request, so the netty
     * eventgroup is shutdown. Otherwise it has to be done manually - but then the same object can be used to perform
     * multiple requests.
     * 
     * @param autoclose
     */
    public void setAutoclose(boolean autoclose) {
        this.autoclose = autoclose;
    }

    public void addCookie(Cookie c) {
        httpClient.addCookie(c);
    }

    public List<Cookie> getCookies() {
        return httpClient.getCookies();
    }

    public CompletableFuture<BulkRestDataSender> doBulkSendRequest(String resource, HttpMethod method) {
        try {
            return httpClient.doBulkSendRequest(serverURL + "/api" + resource, method);
        } catch (ClientException | IOException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isInsecureTls() {
        return httpClient.isInsecureTls();
    }

    /**
     * if true and https connections are used, do not verify server certificate
     * 
     * @param insecureTls
     */
    public void setInsecureTls(boolean insecureTls) {
        httpClient.setInsecureTls(insecureTls);
    }

    /**
     * In case of https connections, this file contains the CA certificates that are used to verify server certificate.
     * 
     * If this is not set, java will use the default mechanism with the trustStore that can be configured via the
     * javax.net.ssl.trustStore system property.
     * 
     * @param caCertFile
     */
    public void setCaCertFile(String caCertFile) throws IOException, GeneralSecurityException {
        httpClient.setCaCertFile(caCertFile);
    }
}
