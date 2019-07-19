package org.yamcs.utils;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class FileUtils {

    public static void deleteRecursively(String path) throws IOException {
        deleteRecursively(new File(path).toPath());
    }

    public static void deleteRecursively(File f) throws IOException {
        deleteRecursively(f.toPath());
    }

    public static void deleteRecursively(Path dirToRemove) throws IOException {
        Files.walkFileTree(dirToRemove, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void deleteContents(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (!dir.equals(directory)) {
                    Files.delete(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Writes bytes to a file in two phases via a temporary file in the same folder. This will either succeed or fail
     * and leave the original file in place.
     */
    public static void writeAtomic(Path file, byte[] bytes) throws IOException {
        Path swpFile = file.resolveSibling(file.getFileName() + ".yswp");
        try (FileOutputStream out = new FileOutputStream(swpFile.toFile())) {
            out.write(bytes);
            out.flush();

            // Force nothing left in system buffers
            // In case of a full disk this will throw a SyncFailedException
            out.getFD().sync();

            Files.move(swpFile, file, ATOMIC_MOVE);
        }
    }
}
