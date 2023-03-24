package org.yamcs.tse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.yamcs.YConfiguration;
import org.yamcs.tse.api.TseCommand;
import org.yamcs.utils.YObjectLoader;

public abstract class InstrumentDriver {

    private static final int DEFAULT_POLLING_INTERVAL = 20;

    protected String instrument;

    protected String commandSeparation;
    protected String responseTermination;
    protected int responseTimeout = 3000;
    protected List<Interceptor> interceptors = new ArrayList<>();

    private int pollingInterval;

    protected Charset encoding = StandardCharsets.US_ASCII;

    public void init(String name, YConfiguration config) {
        this.instrument = name;

        if (config.containsKey("commandSeparation")) {
            commandSeparation = config.getString("commandSeparation");
        }
        if (config.containsKey("responseTermination")) {
            responseTermination = config.getString("responseTermination");
        }
        if (config.containsKey("responseTimeout")) {
            responseTimeout = config.getInt("responseTimeout");
        }

        String requestTermination = config.getString("requestTermination", getDefaultRequestTermination());
        if (requestTermination != null) {
            Map<String, Object> interceptorConfig = new HashMap<>();
            interceptorConfig.put(RequestTerminator.CONFIG_TERMINATION, requestTermination);
            interceptors.add(new RequestTerminator(YConfiguration.wrap(interceptorConfig)));
        }
        if (config.containsKey("interceptors")) {
            for (YConfiguration interceptorConfig : config.getConfigList("interceptors")) {
                try {
                    Interceptor interceptor = YObjectLoader.loadObject(interceptorConfig.toMap());
                    interceptors.add(interceptor);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }

        pollingInterval = Math.min(DEFAULT_POLLING_INTERVAL, responseTimeout);
    }

    public String getName() {
        return instrument;
    }

    public String getCommandSeparation() {
        return commandSeparation;
    }

    public String getResponseTermination() {
        return responseTermination;
    }

    public int getResponseTimeout() {
        return responseTimeout;
    }

    public List<String> command(String command, TseCommand metadata, boolean expectResponse)
            throws IOException, TimeoutException {
        try {
            connect();
            byte[] bytes = command.getBytes();
            for (Interceptor interceptor : interceptors) {
                bytes = interceptor.interceptCommand(metadata, bytes, encoding);
            }
            write(bytes);
            if (expectResponse) {
                ResponseBuffer responseBuffer = new ResponseBuffer(getResponseTermination(), isFragmented());
                if (commandSeparation == null) {
                    byte[] response = readSingleResponse(responseBuffer);
                    if (response != null) {
                        for (Interceptor interceptor : interceptors) {
                            response = interceptor.interceptResponse(metadata, response, encoding);
                        }
                        return Arrays.asList(new String(response, encoding));
                    }
                } else { // Compound command where distinct responses are sent
                    String[] parts = command.split(commandSeparation);
                    List<String> responses = new ArrayList<>();
                    for (String part : parts) {
                        if (part.contains("?") || part.contains("!")) {
                            byte[] response = readSingleResponse(responseBuffer);
                            if (response != null) {
                                for (Interceptor interceptor : interceptors) {
                                    response = interceptor.interceptResponse(metadata, response, encoding);
                                }
                                responses.add(new String(response, encoding));
                            }
                        }
                    }
                    return responses;
                }
            }

            return Collections.emptyList();
        } catch (Exception e) {
            disconnect();
            throw e;
        }
    }

    /**
     * Attemps to read a full delimited TSE response by triggering repeated read polls on the underlying transport,
     * until either a full response was assembled, or the global response timeout has been reached.
     */
    private byte[] readSingleResponse(ResponseBuffer responseBuffer) throws IOException, TimeoutException {
        byte[] response = responseBuffer.readSingleResponse();
        if (response != null) {
            return response;
        }

        long time = System.currentTimeMillis();
        long timeoutTime = time + responseTimeout;

        while (System.currentTimeMillis() < timeoutTime) {
            readAvailable(responseBuffer, pollingInterval);

            response = responseBuffer.readSingleResponse();
            if (response != null) {
                return response;
            }
        }

        // Timed out. Return whatever we have.
        response = responseBuffer.readSingleResponse(true);
        if (getResponseTermination() == null) {
            return response;
        } else {
            throw new TimeoutException(response != null
                    ? "Unterminated response: " + new String(response, encoding)
                    : null);
        }
    }

    public abstract void connect() throws IOException;

    public abstract void disconnect() throws IOException;

    public abstract void write(byte[] cmd) throws IOException;

    public abstract void readAvailable(ResponseBuffer buffer, int timeout) throws IOException;

    /**
     * Returns whether this driver may require reassembly of multiple received fragments in order to obtain a full
     * response.
     * <p>
     * Setting this to false, will allow to have a quick response, even if there is no response termination characters.
     * That is, without needing to wait on timeouts.
     */
    public abstract boolean isFragmented();

    /**
     * Returns the driver-specific default pattern for terminating requests. This is the termination that gets used if
     * the user does not explicitly configure anything.
     * <p>
     * Return {@code null} to do no request termination.
     */
    public abstract String getDefaultRequestTermination();
}
