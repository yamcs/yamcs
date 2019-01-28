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

    protected String name;

    protected String commandSeparation;
    protected String responseTermination;
    protected int responseTimeout = 3000;

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
        connect();
        log.info("{} <<< {}", name, command);
        write(command);
        if (expectResponse) {
            if (commandSeparation == null) {
                String response = read();
                if (response != null) {
                    log.info("{} >>> {}", name, response);
                    return Arrays.asList(response);
                }
            } else { // Compound command where distinct responses are sent
                String[] parts = command.split(commandSeparation);
                List<String> responses = new ArrayList<>();
                for (String part : parts) {
                    if (part.contains("?") || part.contains("!")) {
                        String response = read();
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
    }

    public abstract void connect() throws IOException;

    public abstract void disconnect() throws IOException;

    public abstract void write(String cmd) throws IOException;

    public abstract String read() throws IOException, TimeoutException;
}
