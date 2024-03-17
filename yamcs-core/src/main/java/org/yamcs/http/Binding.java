package org.yamcs.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLException;

import org.yamcs.YConfiguration;

import com.google.common.io.ByteStreams;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

public class Binding {

    private InetAddress address;
    private int port;
    private List<String> tlsCerts;
    private String tlsKey;

    public Binding(int port) {
        this(null, port);
    }

    public Binding(InetAddress address, int port) {
        this.address = address;
        this.port = port;
    }

    public boolean isTLS() {
        return tlsKey != null;
    }

    public void setTLS(String tlsCert, String tlsKey) {
        setTLS(Arrays.asList(tlsCert), tlsKey);
    }

    public void setTLS(List<String> tlsCerts, String tlsKey) {
        this.tlsCerts = tlsCerts;
        this.tlsKey = tlsKey;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    SslContext createSslContext() throws SSLException, IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (String cert : tlsCerts) {
            try (InputStream certIn = Files.newInputStream(Paths.get(cert))) {
                ByteStreams.copy(certIn, buf);
            }
        }

        try (InputStream chain = new ByteArrayInputStream(buf.toByteArray());
                InputStream key = new FileInputStream(tlsKey)) {
            return SslContextBuilder
                    .forServer(chain, key)
                    .build();
        }
    }

    public static Binding fromConfig(YConfiguration config) throws UnknownHostException {
        InetAddress address = null;
        if (config.containsKey("address")) {
            address = InetAddress.getByName(config.getString("address"));
        }

        int port = config.getInt("port");
        Binding binding = new Binding(address, port);
        if (config.containsKey("tlsCert")) {
            List<String> tlsCerts = config.getList("tlsCert");
            String tlsKey = config.getString("tlsKey");
            binding.setTLS(tlsCerts, tlsKey);
        }
        return binding;
    }

    /**
     * Returns a URL string in the format {@code PROTOCOL://ADDRESS} or {@code PROTOCOL://ADDRESS:PORT} if the PORT is
     * unconventional for the PROTOCOL.
     */
    public String getURI() {
        String host;
        if (address != null && !address.isAnyLocalAddress()) {
            host = address.getHostName();
        } else {
            host = "localhost";
        }

        var b = new StringBuilder();
        if (isTLS()) {
            b.append("https://").append(host);
            if (port != 443) {
                b.append(":").append(port);
            }
        } else {
            b.append("http://").append(host);
            if (port != 80) {
                b.append(":").append(port);
            }
        }
        return b.toString();
    }

    @Override
    public String toString() {
        return getURI();
    }
}
