package org.yamcs.utils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Stream;

/**
 * Finds a list of files according to a glob pattern.
 * <p>
 * Not thread safe!
 * 
 * @author nm
 *
 */
public class GlobFileFinder {
    List<Path> result;
    long ioLimit = 1000;
    long ioCount;

    /**
     * Find a list of regular files (not directories) which match glob pattern.
     * <p>
     * The pattern can contain {@code { }, *, ?, [ ]}.
     * <p>
     * {@code **} for recursive directory matching is not supported.
     * <p>
     * The find will limit the number of operations in order to avoid too much I/O due to a pattern requiring lot of I/O
     * operations. A IO operation is roughly file stat.
     * 
     * @param pattern
     * @return
     */
    public List<Path> find(String pattern) {
        result = new ArrayList<Path>();
        ioCount = 0;

        Path patternPath = FileSystems.getDefault().getPath(pattern);
        List<Path> plist = new ArrayList<Path>();
        patternPath.forEach(plist::add);

        ListIterator<Path> pitr = plist.listIterator();
        if (pitr.hasNext()) {
            Path current = patternPath.isAbsolute() ? patternPath.getRoot() : FileSystems.getDefault().getPath("");
            findMatchingFiles(current, pitr);
        }
        return result;
    }

    /**
     * Set the maximum number of IO operations performed when matching a pattern.
     * 
     * @param ioLimit
     */
    public void setIoLimit(int ioLimit) {
        this.ioLimit = ioLimit;
    }

    private void findMatchingFiles(Path current, ListIterator<Path> pitr) throws UncheckedIOException {
        if (!pitr.hasNext()) {
            if(Files.isRegularFile(current)) {
                result.add(current);
            }
            return;
        }

        Path pattern = pitr.next();
        String patterns = pattern.toString();

        if (isPattern(patterns)) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + patterns);
            try (Stream<Path> files =Files.list(current)){
                        files.filter(p -> incrIoCount())
                            .map(p -> p.getFileName())
                            .filter(p -> matcher.matches(p))
                            .map(p -> current.resolve(p))
                            .filter(p -> Files.isDirectory(p) == pitr.hasNext())
                            .forEach(p -> findMatchingFiles(p, pitr));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else if (Files.exists(current.resolve(pattern))) {
            incrIoCount();
            findMatchingFiles(current.resolve(pattern), pitr);
        }
        pitr.previous();
    }

    private boolean incrIoCount() {
        if (++ioCount >= ioLimit) {
            throw new IllegalArgumentException("Pattern require too many IO operations");
        }
        return true;
    }

    private boolean isPattern(String p) {
        return Arrays.asList('*', '?', '[', '{').stream().anyMatch(c -> p.indexOf(c) >= 0);
    }
}
