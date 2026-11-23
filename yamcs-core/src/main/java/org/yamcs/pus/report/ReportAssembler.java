package org.yamcs.pus.report;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;

import org.yamcs.protobuf.satellite.ReportCommand;
import org.yamcs.protobuf.satellite.ReportPage;
import org.yamcs.protobuf.satellite.ReportRow;
import org.yamcs.protobuf.satellite.ReportSnapshot;
import org.yamcs.protobuf.satellite.ReportStatus;
import org.yamcs.utils.TimeEncoding;

/** Collects the evidence for one request after attribution has been decided. */
final class ReportAssembler {
    private static final String DIFFERENT_TOTALS = "Packets disagree on the total page count.";
    private static final String MULTIPAGE_EMPTY = "An empty report must be sent as page 1 of 1.";
    private static final String INTERRUPTED = "Report collection was interrupted before the response window closed.";

    record Page(int number, int total, long receptionTime, long generationTime, int apid, int sourceSequenceCount,
            List<ReportRow> rows, String issue) {
    }

    static final class Attempt {
        private final ReportSnapshot identity;
        private final TreeMap<Integer, ReportPage> pages = new TreeMap<>();
        private final HashSet<Integer> rejectedPages = new HashSet<>();
        private final LinkedHashSet<String> issues = new LinkedHashSet<>();
        private final long sentTime;
        private final long deadline;
        private Long firstReceived;
        private Long lastReceived;
        private int expectedPages;
        private int withheldPackets;

        Attempt(String type, String instance, String packetName, ReportCommand command,
                long sentTime, long deadline, String echo) {
            this(identity(type, instance, packetName, command, sentTime, deadline, echo));
        }

        Attempt(ReportSnapshot saved) {
            identity = ReportSnapshot.newBuilder().setId(saved.getId()).setType(saved.getType())
                    .setCommand(saved.getCommand()).setRequestedAt(saved.getRequestedAt())
                    .setResponseDeadline(saved.getResponseDeadline())
                    .setEcho(saved.getEcho()).setPacketName(saved.getPacketName()).build();
            sentTime = TimeEncoding.fromProtobufTimestamp(saved.getRequestedAt());
            deadline = TimeEncoding.fromProtobufTimestamp(saved.getResponseDeadline());
            for (var page : saved.getPagesList()) {
                pages.put(page.getPageNumber(), page);
            }
            rejectedPages.addAll(saved.getRejectedPagesList());
            issues.addAll(saved.getIssuesList());
            expectedPages = saved.getExpectedPages();
            withheldPackets = saved.getWithheldPackets();
            firstReceived = saved.hasFirstReceived() ? TimeEncoding.fromProtobufTimestamp(saved.getFirstReceived())
                    : null;
            lastReceived = saved.hasLastReceived() ? TimeEncoding.fromProtobufTimestamp(saved.getLastReceived()) : null;
        }

        private static ReportSnapshot identity(String type, String instance, String packetName, ReportCommand command,
                long sentTime, long deadline, String echo) {
            String key = instance + "\n" + type + "\n" + command.getId();
            return ReportSnapshot.newBuilder()
                    .setId(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString())
                    .setType(type).setPacketName(packetName).setCommand(command)
                    .setRequestedAt(TimeEncoding.toProtobufTimestamp(sentTime))
                    .setResponseDeadline(TimeEncoding.toProtobufTimestamp(deadline))
                    .setEcho(echo == null ? "" : echo).build();
        }

        String id() {
            return identity.getId();
        }

        String requestKey() {
            return identity.getType() + ":" + identity.getCommand().getId();
        }

        long deadline() {
            return deadline;
        }

        long sortTime() {
            return lastReceived != null ? lastReceived : sentTime;
        }

        boolean matches(String type, long received, String echo, long preAckAllowance) {
            return identity.getType().equals(type)
                    && identity.getEcho().equals(echo == null ? "" : echo)
                    && received >= sentTime - preAckAllowance && received < deadline;
        }

        void accept(Page page) {
            observe(page.receptionTime());
            if (page.total() < 1 || page.total() > 0xffff || page.number() < 1 || page.number() > page.total()) {
                issues.add(page.issue() == null ? "Invalid page number or total page count." : page.issue());
                return;
            }
            if (expectedPages == 0 && !issues.contains(DIFFERENT_TOTALS)) {
                expectedPages = page.total();
            }
            if (expectedPages != 0 && page.total() != expectedPages) {
                expectedPages = 0;
                issues.add(DIFFERENT_TOTALS);
                reject(page.number());
                return;
            }
            if (page.issue() != null) {
                issues.add(page.issue());
                reject(page.number());
                return;
            }
            if (rejectedPages.contains(page.number())) {
                return;
            }
            var existing = pages.get(page.number());
            if (existing != null) {
                if (!existing.getRowsList().equals(page.rows())) {
                    issues.add("Conflicting payloads for page " + page.number() + ".");
                    reject(page.number());
                }
                return;
            }
            var received = ReportPage.newBuilder().setPageNumber(page.number())
                    .setApid(page.apid()).setSourceSequenceCount(page.sourceSequenceCount())
                    .setReceptionTime(TimeEncoding.toProtobufTimestamp(page.receptionTime()))
                    .addAllRows(page.rows());
            if (page.generationTime() != TimeEncoding.INVALID_INSTANT) {
                received.setGenerationTime(TimeEncoding.toProtobufTimestamp(page.generationTime()));
            }
            pages.put(page.number(), received.build());
        }

        private void reject(int number) {
            pages.remove(number);
            rejectedPages.add(number);
        }

        void withhold(long receptionTime) {
            observe(receptionTime);
            withheldPackets++;
        }

        void issue(String message) {
            issues.add(message);
        }

        private void observe(long receptionTime) {
            firstReceived = firstReceived == null ? receptionTime : Math.min(firstReceived, receptionTime);
            lastReceived = lastReceived == null ? receptionTime : Math.max(lastReceived, receptionTime);
        }

        ReportSnapshot checkpoint() {
            var snapshot = identity.toBuilder().addAllPages(pages.values()).addAllReceivedPages(pages.keySet())
                    .addAllIssues(issues).addAllRejectedPages(rejectedPages.stream().sorted().toList())
                    .setWithheldPackets(withheldPackets);
            if (expectedPages > 0) {
                snapshot.setExpectedPages(expectedPages);
            }
            if (firstReceived != null) {
                snapshot.setFirstReceived(TimeEncoding.toProtobufTimestamp(firstReceived));
                snapshot.setLastReceived(TimeEncoding.toProtobufTimestamp(lastReceived));
            }
            return snapshot.build();
        }

        ReportSnapshot finish(long finalizedAt, boolean interrupted) {
            var snapshot = checkpoint().toBuilder().setFinalizedAt(TimeEncoding.toProtobufTimestamp(finalizedAt));
            if (expectedPages > 0) {
                for (int number = 1; number <= expectedPages; number++) {
                    if (!pages.containsKey(number)) {
                        snapshot.addMissingPages(number);
                    }
                }
            }
            boolean allPagesReceived = expectedPages > 0 && pages.size() == expectedPages;
            boolean invalidEmpty = allPagesReceived && expectedPages > 1
                    && pages.values().stream().allMatch(page -> page.getRowsCount() == 0);
            if (invalidEmpty) {
                snapshot.addIssues(MULTIPAGE_EMPTY);
            }
            if (!interrupted && issues.isEmpty() && allPagesReceived && !invalidEmpty) {
                long assembled = pages.values().stream()
                        .mapToLong(p -> TimeEncoding.fromProtobufTimestamp(p.getReceptionTime())).max().orElseThrow();
                snapshot.setStatus(ReportStatus.COMPLETE).setAssembledAt(TimeEncoding.toProtobufTimestamp(assembled));
            } else if (withheldPackets > 0) {
                snapshot.setStatus(ReportStatus.AMBIGUOUS);
            } else if (!interrupted && firstReceived == null) {
                snapshot.setStatus(ReportStatus.NO_RESPONSE);
            } else {
                snapshot.setStatus(ReportStatus.PARTIAL);
            }
            if (interrupted) {
                snapshot.addIssues(INTERRUPTED);
            }
            return snapshot.build();
        }
    }
}
