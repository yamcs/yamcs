package org.yamcs.tse;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;

public abstract class InstrumentDriver {

    private static final Logger log = LoggerFactory.getLogger(InstrumentDriver.class);

    private static final int DEFAULT_POLLING_INTERVAL = 20;

    protected String name;

    protected String commandSeparation;
    protected String responseTermination;
    protected int responseTimeout = 3000;

    private int pollingInterval;

    protected Charset encoding = StandardCharsets.US_ASCII;

    public InstrumentDriver(String name, Map<String, Object> args) {
        this.name = name;

        if (args.containsKey("commandSeparation")) {
            commandSeparation = YConfiguration.getString(args, "commandSeparation");
        }
        if (args.containsKey("responseTermination")) {
            responseTermination = YConfiguration.getString(args, "responseTermination");
        }
        if (args.containsKey("responseTimeout")) {
            responseTimeout = YConfiguration.getInt(args, "responseTimeout");
        }

        pollingInterval = Math.min(DEFAULT_POLLING_INTERVAL, responseTimeout);
    }

    public String getName() {
        return name;
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

    public List<String> command(String command, boolean expectResponse) throws IOException, TimeoutException {
        try {
            connect();
            log.info("{} <<< {}", name, command);
            write(command);
            if (expectResponse) {
                ResponseBuffer responseBuffer = new ResponseBuffer(encoding, getResponseTermination());
                if (commandSeparation == null) {
                    String response = readSingleResponse(responseBuffer);
                    if (response != null) {
                        log.info("{} >>> {}", name, response);
                        return Arrays.asList(response);
                    }
                } else { // Compound command where distinct responses are sent
                    String[] parts = command.split(commandSeparation);
                    List<String> responses = new ArrayList<>();
                    for (String part : parts) {
                        if (part.contains("?") || part.contains("!")) {
                            String response = readSingleResponse(responseBuffer);
                            if (response != null) {
                                log.info("{} >>> {}", name, response);
                                responses.add(response);
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
    private String readSingleResponse(ResponseBuffer responseBuffer) throws IOException, TimeoutException {
        String response = responseBuffer.readSingleResponse();
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
            throw new TimeoutException(response != null ? "Unterminated response: " + response : null);
        }
    }

    public abstract void connect() throws IOException;

    public abstract void disconnect() throws IOException;

    public abstract void write(String cmd) throws IOException;

    public abstract void readAvailable(ResponseBuffer buffer, int timeout) throws IOException;
}
