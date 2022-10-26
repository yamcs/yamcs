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

    // Unlikely escape patterns for a glob wildcards
    private static final String GLOB_ANY = "*";
    private static final String GLOB_ANY_ESCAPE = "__GLOB_ANY__";
    private static final String GLOB_ANY_SINGLE = "?";
    private static final String GLOB_ANY_SINGLE_ESCAPE = "__GLOB_SINGLE_ANY__";

    List<Path> result;
    long ioLimit = 1000;
    long ioCount;

    /**
     * Calls {@link #find(Path, String)} for the current working directory.
     */
    public List<Path> find(String pattern) {
        Path start = FileSystems.getDefault().getPath("");
        return find(start, pattern);
    }

    /**
     * Find a list of regular files (not directories) which match glob pattern.
     * <p>
     * The pattern can contain {@code { }, *, ?, [ ]}.
     * <p>
     * {@code **} for recursive directory matching is not supported.
     * <p>
     * The find will limit the number of operations in order to avoid too much I/O due to a pattern requiring lot of I/O
     * operations. An I/O operation is roughly file stat.
     * 
     * @param pattern
     *            glob pattern
     * 
     * @return Matched files
     */
    public List<Path> find(Path start, String pattern) {
        result = new ArrayList<Path>();
        ioCount = 0;

        // Use a Path to split the pattern in segments.
        // On windows, Path refuses some glob symbols, so temporarily escape them.
        pattern = escapePattern(pattern);
        Path patternPath = start.getFileSystem().getPath(pattern);
        List<String> plist = new ArrayList<>();
        patternPath.forEach(segment -> {
            plist.add(restorePattern(segment.toString()));
        });

        ListIterator<String> pitr = plist.listIterator();
        if (pitr.hasNext()) {
            Path current = patternPath.isAbsolute() ? patternPath.getRoot() : start;
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

    private void findMatchingFiles(Path current, ListIterator<String> pitr) throws UncheckedIOException {
        if (!pitr.hasNext()) {
            if (Files.isRegularFile(current)) {
                result.add(current);
            }
            return;
        }

        String segment = pitr.next();

        if (isPattern(segment)) {
            PathMatcher matcher = current.getFileSystem().getPathMatcher("glob:" + segment);
            try (Stream<Path> files = Files.list(current)) {
                files.filter(p -> incrIoCount())
                        .map(p -> p.getFileName())
                        .filter(p -> matcher.matches(p))
                        .map(p -> current.resolve(p))
                        .filter(p -> Files.isDirectory(p) == pitr.hasNext())
                        .forEach(p -> findMatchingFiles(p, pitr));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else if (Files.exists(current.resolve(segment))) {
            incrIoCount();
            findMatchingFiles(current.resolve(segment), pitr);
        }
        pitr.previous();
    }

    private boolean incrIoCount() {
        if (++ioCount >= ioLimit) {
            throw new IllegalArgumentException("Pattern requires too many I/O operations");
        }
        return true;
    }

    /**
     * Escape '?' and '*' glob symbols to form a 'legal'-appearing path.
     */
    private static String escapePattern(String pattern) {
        pattern = pattern.replace(GLOB_ANY, GLOB_ANY_ESCAPE);
        pattern = pattern.replace(GLOB_ANY_SINGLE, GLOB_ANY_SINGLE_ESCAPE);
        return pattern;
    }

    /**
     * Restore a glob that previously passed through {@link #escapePattern(String)}
     */
    private static String restorePattern(String pattern) {
        pattern = pattern.replace(GLOB_ANY_ESCAPE, GLOB_ANY);
        pattern = pattern.replace(GLOB_ANY_SINGLE_ESCAPE, GLOB_ANY_SINGLE);
        return pattern;
    }

    private static boolean isPattern(String p) {
        return Arrays.asList('*', '?', '[', '{').stream().anyMatch(c -> p.indexOf(c) >= 0);
    }
}
