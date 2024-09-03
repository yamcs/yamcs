package org.yamcs.utils;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class FileUtils {

    public static void copyRecursively(Path source, Path target, CopyOption... options) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), options);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void deleteRecursively(Path dirToRemove) throws IOException {
        if (!Files.exists(dirToRemove)) {
            return;
        }
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

    public static void deleteRecursivelyIfExists(Path path) throws IOException {
        if (Files.exists(path)) {
            deleteRecursively(path);
        }
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

    /**
     * Return the extension for a file. Always in lowercase.
     */
    public static String getFileExtension(Path file) {
        var fileName = file.getFileName().toString();
        var idx = fileName.lastIndexOf('.');
        if (idx != -1 && idx != fileName.length() - 1) {
            return fileName.substring(idx + 1).toLowerCase();
        } else {
            return null;
        }
    }
}
