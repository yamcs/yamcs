package org.yamcs.cascading;

import org.yamcs.client.Page;
import org.yamcs.client.mdb.MissionDatabaseClient;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.Mdb.ContainerInfo;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.*;
import java.util.concurrent.CompletableFuture;


/**
 * Responsible for retrieving the list of containers from the upstream server
 */
public class ContainerFetcher {

    public static CompletableFuture<List<String>> fetch(MissionDatabaseClient mdbClient, Log log) {
        return getPages(mdbClient.listContainers(), null, log);
    }

    private static CompletableFuture<List<String>> getPages(CompletableFuture<Page<ContainerInfo>> future, List<String> list, Log log) {
        List<String> containers = Objects.requireNonNullElse(list, new ArrayList<>());
        return future.thenCompose(page -> {
            page.forEach(container -> containers.add(container.getQualifiedName()));

            if(page.hasNextPage()) {
                return getPages(page.getNextPage(), containers, log);
            } else {
                return CompletableFuture.completedFuture(containers);
            }
        });
    }

    public static CompletableFuture<List<String>> fetchAndMatch(MissionDatabaseClient mdbClient, List<String> patterns, Log log) {
        return fetch(mdbClient, log).thenApply(containers -> {
            HashSet<String> filtered = new HashSet<>();

            for (String p : patterns) {
                String pattern = p;
                if(p.endsWith("/")) {
                    pattern += "**";
                }

                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
                boolean added = false;

                for (String container : containers) {
                    if (matcher.matches(Path.of(container))) {
                        filtered.add(container);
                        added = true;
                    }
                }

                if (!added) {
                    log.warn("Cannot match containers with {} in remote MDB; ignoring", p);
                }
            }

            return new ArrayList<>(filtered);
        });
    }

}
