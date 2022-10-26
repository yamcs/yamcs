package org.yamcs.logging;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.logging.ErrorManager;
import java.util.logging.FileHandler;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.XMLFormatter;

/**
 * File handler that watches the file it is logging to. If the file changes, it gets closed and reopened with the same
 * filename.
 * <p>
 * This handler is useful for when log rotation is to be handled outside of the standard Java Logging system, for
 * example with a program like logrotate. Without the watch-functionality it would keep writing to the old (rotated)
 * file.
 */
public class WatchedFileHandler extends Handler {

    private Path watchedFile;
    private FileHandler wrappedHandler;

    private Thread fileWatcher;

    public WatchedFileHandler() throws IOException {
        String filename = getProperty("filename", "yamcs.log");
        watchedFile = Paths.get(filename);

        wrappedHandler = new FileHandler(filename, true /* append */);
        setLevel(getLevelProperty("level", Level.ALL));
        setFilter(getFilterProperty("filter", null));
        setFormatter(getFormatterProperty("formatter", new XMLFormatter()));
        try {
            setEncoding(getProperty("encoding", null));
        } catch (SecurityException | UnsupportedEncodingException e) {
            // Ignore
        }

        fileWatcher = new FileWatcher();
        fileWatcher.start();
    }

    private synchronized void reopenFile() {
        Level oldLevel = getLevel();
        Filter oldFilter = getFilter();
        Formatter oldFormatter = getFormatter();
        String oldEncoding = getEncoding();

        setLevel(Level.OFF);

        wrappedHandler.close();
        try {
            wrappedHandler = new FileHandler(watchedFile.toString(), true);
        } catch (IOException e) {
            // Avoid throwing
            reportError(null, e, ErrorManager.OPEN_FAILURE);
        }

        setFilter(oldFilter);
        setFormatter(oldFormatter);
        try {
            setEncoding(oldEncoding);
        } catch (SecurityException | UnsupportedEncodingException e) {
            // Ignore
        }
        setLevel(oldLevel);
    }

    @Override
    public synchronized void publish(LogRecord record) {
        wrappedHandler.publish(record);
    }

    @Override
    public synchronized void flush() {
        wrappedHandler.flush();
    }

    @Override
    public synchronized void close() throws SecurityException {
        fileWatcher.interrupt();
        wrappedHandler.close();
    }

    @Override
    public synchronized void setEncoding(String encoding) throws SecurityException, UnsupportedEncodingException {
        wrappedHandler.setEncoding(encoding);
    }

    @Override
    public synchronized void setFormatter(Formatter newFormatter) throws SecurityException {
        wrappedHandler.setFormatter(newFormatter);
    }

    @Override
    public synchronized void setErrorManager(ErrorManager em) {
        wrappedHandler.setErrorManager(em);
    }

    @Override
    public synchronized void setFilter(Filter newFilter) throws SecurityException {
        wrappedHandler.setFilter(newFilter);
    }

    @Override
    public synchronized void setLevel(Level newLevel) throws SecurityException {
        wrappedHandler.setLevel(newLevel);
    }

    @Override
    public boolean isLoggable(LogRecord record) {
        return wrappedHandler.isLoggable(record);
    }

    @Override
    public String getEncoding() {
        return wrappedHandler.getEncoding();
    }

    @Override
    public ErrorManager getErrorManager() {
        return wrappedHandler.getErrorManager();
    }

    @Override
    public Filter getFilter() {
        return wrappedHandler.getFilter();
    }

    @Override
    public Level getLevel() {
        return wrappedHandler.getLevel();
    }

    @Override
    public Formatter getFormatter() {
        return wrappedHandler.getFormatter();
    }

    private Level getLevelProperty(String name, Level defaultValue) {
        String val = getProperty(name, null);
        if (val == null) {
            return defaultValue;
        }
        Level l = Level.parse(val.trim());
        return l != null ? l : defaultValue;
    }

    private Filter getFilterProperty(String name, Filter defaultValue) {
        String val = getProperty(name, null);
        try {
            if (val != null) {
                Class<?> clz = ClassLoader.getSystemClassLoader().loadClass(val);
                return (Filter) clz.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            // Ignore
        }
        return defaultValue;
    }

    private Formatter getFormatterProperty(String name, Formatter defaultValue) {
        String val = getProperty(name, null);
        try {
            if (val != null) {
                Class<?> clz = ClassLoader.getSystemClassLoader().loadClass(val);
                return (Formatter) clz.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            // Ignore
        }
        return defaultValue;
    }

    private String getProperty(String name, String defaultValue) {
        LogManager manager = LogManager.getLogManager();
        String qname = getClass().getName() + "." + name;
        String property = manager.getProperty(qname);
        return property != null ? property : defaultValue;
    }

    private class FileWatcher extends Thread {

        @Override
        public void run() {
            Path parent = watchedFile.getParent();
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                parent.register(watchService, StandardWatchEventKinds.ENTRY_DELETE);
                while (true) {
                    WatchKey key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path relpath = (Path) event.context();
                        if (parent.resolve(relpath).equals(watchedFile)) {
                            reopenFile();
                        }
                    }
                    key.reset();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
