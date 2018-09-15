package org.yamcs.tse;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;

public abstract class InstrumentDriver {

    private static final Logger log = LoggerFactory.getLogger(InstrumentDriver.class);

    protected String name;

    protected String responseTermination;
    protected int responseTimeout = 3000;

    protected Charset encoding = StandardCharsets.US_ASCII;

    public InstrumentDriver(String name, Map<String, Object> args) {
        this.name = name;

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

    public String getResponseTermination() {
        return responseTermination;
    }

    public int getResponseTimeout() {
        return responseTimeout;
    }

    public String command(String command, boolean expectResponse) throws IOException, TimeoutException {
        connect();
        log.info("{} <<< {}", name, command);
        write(command);
        if (expectResponse) {
            String response = read();
            if (response != null) {
                log.info("{} >>> {}", name, response);
            }
            return response;
        }

        return null;
    }

    public abstract void connect() throws IOException;

    public abstract void disconnect() throws IOException;

    public abstract void write(String cmd) throws IOException;

    public abstract String read() throws IOException, TimeoutException;
}
