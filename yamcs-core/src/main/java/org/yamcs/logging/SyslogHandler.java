package org.yamcs.logging;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

/**
 * JUL appender that emits to syslogd over UDP with messages formatted according to RFC 3164 (BSD syslog).
 */
public class SyslogHandler extends Handler {

    private static final String TAG = "yamcsd";

    private SimpleDateFormat sdf = new SimpleDateFormat("MMM dd HH:mm:ss", Locale.US);
    private Date d = new Date();

    private DatagramSocket socket;

    private InetAddress host;
    private int port;

    private int facility;
    private String hostname;

    public SyslogHandler() throws SocketException, UnknownHostException {
        sdf.setTimeZone(TimeZone.getDefault()); // emit local time as per RFC 3164
        socket = new DatagramSocket();
        hostname = InetAddress.getLocalHost().getHostName();

        String host = getProperty("host", null);
        if (host == null) {
            setHost(InetAddress.getLoopbackAddress());
        } else {
            setHost(InetAddress.getByName(host));
        }

        setPort(getIntProperty("port", 514));
        setLevel(getLevelProperty("level", Level.ALL));
        setFacility(getIntProperty("facility", 1)); // 1 == user-level messages
    }

    public void setHost(InetAddress host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setFacility(int facility) {
        this.facility = facility;
    }

    private int getIntProperty(String name, int defaultValue) {
        String property = getProperty(name, Integer.toString(defaultValue));
        return Integer.parseInt(property);
    }

    private Level getLevelProperty(String name, Level defaultValue) {
        String val = getProperty(name, null);
        if (val == null) {
            return defaultValue;
        }
        Level l = Level.parse(val.trim());
        return l != null ? l : defaultValue;
    }

    private String getProperty(String name, String defaultValue) {
        LogManager manager = LogManager.getLogManager();
        String qname = getClass().getName() + "." + name;
        String property = manager.getProperty(qname);
        return property != null ? property : defaultValue;
    }

    @Override
    public synchronized void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }

        StringBuilder buf = new StringBuilder();
        int pri = (facility * 8) + toSeverity(record.getLevel());
        buf.append("<").append(pri).append(">");

        d.setTime(record.getMillis());
        buf.append(sdf.format(d));
        buf.append(' ');

        buf.append(hostname);
        buf.append(' ');

        buf.append(TAG); // TODO add pid when leaving jdk8: "yamcsd[PID]"

        if (record.getMessage() != null) {
            buf.append(": ");
            buf.append(record.getMessage());
        }

        byte[] b = buf.toString().getBytes();
        DatagramPacket packet = new DatagramPacket(b, b.length, host, port);
        try {
            socket.send(packet);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int toSeverity(Level level) {
        if (level == Level.SEVERE) {
            return 3; // Error
        } else if (level == Level.WARNING) {
            return 4; // Warning
        } else if (level == Level.FINE || level == Level.FINER || level == Level.FINEST) {
            return 7; // Debug
        } else {
            return 6; // Informational
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws SecurityException {
        if (socket != null) {
            socket.close();
        }
    }
}
