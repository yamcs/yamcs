package org.yamcs.yarch;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public class BackupUtils {

    public static void verifyBackupDirectory(String backupDir, boolean mustExist) throws IOException {
        Path path = FileSystems.getDefault().getPath(backupDir);
        if (path.toFile().exists()) {
            if (!path.toFile().isDirectory()) {
                throw new FileSystemException(backupDir, null,
                        "File '" + backupDir + "' exists and is not a directory");
            }

            boolean isEmpty = true;
            boolean isBackupDir = false;
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(path)) {
                for (Path p : dirStream) {
                    isEmpty = false;
                    if (p.endsWith("meta")) {
                        isBackupDir = true;
                        break;
                    }
                }
            }

            if (!isEmpty && !isBackupDir) {
                throw new FileSystemException(backupDir, null,
                        "Directory '" + backupDir + "' is not a backup directory");
            }
            if (!Files.isWritable(path)) {
                throw new FileSystemException(backupDir, null, "Directory '" + backupDir + "' is not writable");
            }
        } else {
            if (mustExist) {
                throw new FileSystemException(backupDir, null, "Directory '" + backupDir + "' does not exist");
            } else {
                Files.createDirectories(path);
            }
        }
    }
}
