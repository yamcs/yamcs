package org.yamcs.cfdp;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yamcs.cfdp.protobuf.TransferSnapshot;
import org.yamcs.logging.Log;

/**
 * Manages the per-service directory where the CFDP transfers in progress are stored.
 * <p>
 * For each transfer (identified by its unique id) the following files may exist:
 * <ul>
 * <li>{@code <id>.data} and {@code <id>.journal}: the file data (see {@link DiskDataFile}).</li>
 * <li>{@code <id>.state}: a {@link TransferSnapshot} protobuf message with the state of the transfer, written
 * atomically.</li>
 * </ul>
 * The class is direction agnostic: the content of the files is interpreted by the transfers.
 * <p>
 * The operations which force data to the disk (fsync) are slow (milliseconds) so they are performed asynchronously by
 * a dedicated I/O thread: the CFDP state machine just hands over the snapshot to be written
 * ({@link #saveStateAsync(long, AbstractDataFile, TransferSnapshot)}); consecutive snapshots of the same transfer are
 * coalesced. Before writing a snapshot, the data file is synced such that the state never refers to data which is not
 * on disk.
 */
public class CfdpTransferStore {
    static final Log log = new Log(CfdpTransferStore.class);

    public static final String DATA_EXT = ".data";
    public static final String JOURNAL_EXT = ".journal";
    public static final String STATE_EXT = ".state";
    static final String TMP_EXT = ".tmp";
    static final Pattern FILE_PATTERN = Pattern.compile("(\\d+)\\.(data|journal|state)");

    private final Path dir;
    private final ExecutorService ioExecutor;
    private final ConcurrentHashMap<Long, PendingState> pendingStates = new ConcurrentHashMap<>();

    record PendingState(AbstractDataFile dataFile, TransferSnapshot snapshot) {
    }

    public CfdpTransferStore(Path dir) throws IOException {
        this.dir = dir;
        Files.createDirectories(dir);
        this.ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "cfdp-store-" + dir.getFileName());
            t.setDaemon(true);
            return t;
        });
    }

    public Path getDirectory() {
        return dir;
    }

    public Path dataPath(long id) {
        return dir.resolve(id + DATA_EXT);
    }

    public Path journalPath(long id) {
        return dir.resolve(id + JOURNAL_EXT);
    }

    public Path statePath(long id) {
        return dir.resolve(id + STATE_EXT);
    }

    /**
     * Create a new empty data file for the transfer; existing files are truncated.
     */
    public DiskDataFile createDataFile(long id) throws IOException {
        return DiskDataFile.create(dataPath(id), journalPath(id));
    }

    /**
     * Open the existing data file of the transfer, verifying its integrity (see {@link DiskDataFile}).
     *
     * @param maxEnd
     *            data beyond this offset is discarded; -1 for no limit
     */
    public DiskDataFile openDataFile(long id, long maxEnd) throws IOException {
        return DiskDataFile.open(dataPath(id), journalPath(id), maxEnd);
    }

    /**
     * Atomically write the state of a transfer (tmp file + fsync + rename).
     */
    public void writeState(long id, TransferSnapshot snapshot) throws IOException {
        Path path = statePath(id);
        Path tmp = dir.resolve(id + STATE_EXT + TMP_EXT);
        byte[] b = snapshot.toByteArray();
        try (FileChannel ch = FileChannel.open(tmp, CREATE, TRUNCATE_EXISTING, WRITE)) {
            ByteBuffer bb = ByteBuffer.wrap(b);
            while (bb.hasRemaining()) {
                ch.write(bb);
            }
            ch.force(false);
        }
        Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        fsyncDirectory(dir);
    }

    /**
     * Asynchronously syncs the data file (if not null) and then writes the snapshot.
     * <p>
     * If there is already a pending write for the same transfer, the snapshot replaces the pending one.
     */
    public void saveStateAsync(long id, AbstractDataFile dataFile, TransferSnapshot snapshot) {
        if (pendingStates.put(id, new PendingState(dataFile, snapshot)) == null) {
            execute(() -> writePending(id));
        }
    }

    private void writePending(long id) {
        PendingState p = pendingStates.remove(id);
        if (p == null) {
            return;
        }
        try {
            if (p.dataFile != null) {
                p.dataFile.sync();
            }
            writeState(id, p.snapshot);
        } catch (IOException | RuntimeException e) {
            log.error("Failed to save the state of transfer {}", id, e);
        }
    }

    /**
     * Same as {@link #saveStateAsync(long, AbstractDataFile, TransferSnapshot)} but waits for the write to finish.
     */
    public void saveState(long id, AbstractDataFile dataFile, TransferSnapshot snapshot) throws IOException {
        pendingStates.remove(id);
        try {
            ioExecutor.submit(() -> {
                if (dataFile != null) {
                    dataFile.sync();
                }
                writeState(id, snapshot);
                return null;
            }).get();
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof IOException ioe) {
                throw ioe;
            }
            throw new IOException(c);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    /**
     * Asynchronously syncs the data file.
     */
    public void syncAsync(AbstractDataFile dataFile) {
        execute(() -> {
            try {
                dataFile.sync();
            } catch (IOException | RuntimeException e) {
                log.error("Failed to sync {}", dataFile, e);
            }
        });
    }

    /**
     * Asynchronously deletes the data file and all the files of the transfer, after the pending writes.
     */
    public void deleteAsync(long id, AbstractDataFile dataFile) {
        pendingStates.remove(id);
        execute(() -> {
            if (dataFile != null) {
                dataFile.delete();
            }
            delete(id);
        });
    }

    /**
     * Runs a (blocking) I/O task on the I/O thread, after the pending operations.
     */
    public <T> CompletableFuture<T> supplyAsync(Callable<T> task) {
        CompletableFuture<T> cf = new CompletableFuture<>();
        try {
            ioExecutor.execute(() -> {
                try {
                    cf.complete(task.call());
                } catch (Throwable t) {
                    cf.completeExceptionally(t);
                }
            });
        } catch (RejectedExecutionException e) {
            cf.completeExceptionally(e);
        }
        return cf;
    }

    /**
     * Waits for all the pending operations to finish
     */
    public void flush() {
        try {
            ioExecutor.submit(() -> {
            }).get();
        } catch (ExecutionException | RejectedExecutionException e) {
            log.warn("Error flushing the store", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Waits for the pending operations to finish and stops the I/O thread.
     */
    public void close() {
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                log.warn("Timeout waiting for the pending writes to finish");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void execute(Runnable r) {
        try {
            ioExecutor.execute(r);
        } catch (RejectedExecutionException e) {
            log.debug("Store closed, ignoring operation");
        }
    }

    /**
     * Reads the state of the transfer.
     *
     * @return null if there is no state file
     */
    public TransferSnapshot readState(long id) throws IOException {
        Path path = statePath(id);
        if (!Files.exists(path)) {
            return null;
        }
        return TransferSnapshot.parseFrom(Files.readAllBytes(path));
    }

    /**
     * Returns the ids of all the transfers which have at least one file in the directory.
     * <p>
     * Left-over temporary files are removed.
     */
    public SortedSet<Long> listTransferIds() throws IOException {
        SortedSet<Long> ids = new TreeSet<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                if (name.endsWith(TMP_EXT)) {
                    Files.deleteIfExists(p);
                    continue;
                }
                Matcher m = FILE_PATTERN.matcher(name);
                if (m.matches()) {
                    ids.add(Long.parseLong(m.group(1)));
                }
            }
        }
        return ids;
    }

    /**
     * Delete all the files of the given transfer
     */
    public void delete(long id) {
        for (Path p : new Path[] { statePath(id), dataPath(id), journalPath(id) }) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                log.warn("Failed to delete {}", p, e);
            }
        }
    }

    /**
     * fsync a directory such that a rename is durable. Not supported on all platforms, the errors are ignored.
     */
    static void fsyncDirectory(Path dir) {
        try (FileChannel ch = FileChannel.open(dir, READ)) {
            ch.force(true);
        } catch (IOException e) {
            // not supported on some platforms (e.g. Windows)
        }
    }
}
