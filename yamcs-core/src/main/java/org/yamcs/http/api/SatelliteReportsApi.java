package org.yamcs.http.api;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Objects;

import org.yamcs.YamcsServer;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.NotFoundException;
import org.yamcs.protobuf.satellite.AbstractSatelliteReportsApi;
import org.yamcs.protobuf.satellite.GetReportRequest;
import org.yamcs.protobuf.satellite.ListReportTypesRequest;
import org.yamcs.protobuf.satellite.ListReportTypesResponse;
import org.yamcs.protobuf.satellite.ListReportsRequest;
import org.yamcs.protobuf.satellite.ListReportsResponse;
import org.yamcs.protobuf.satellite.ReportCommand;
import org.yamcs.protobuf.satellite.ReportSnapshot;
import org.yamcs.protobuf.satellite.ReportTypeItem;
import org.yamcs.pus.report.ReportProfile;
import org.yamcs.pus.report.SatelliteReportService;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.User;
import org.yamcs.utils.TimeEncoding;

import com.google.gson.Gson;

/** Read access to the satellite report archive of a configured instance. */
public final class SatelliteReportsApi extends AbstractSatelliteReportsApi<Context> {
    private static final Gson GSON = new Gson();

    @Override
    public void listReportTypes(Context ctx, ListReportTypesRequest request,
            Observer<ListReportTypesResponse> observer) {
        var service = service(request.getInstance());
        var response = ListReportTypesResponse.newBuilder();
        for (var profile : service.profiles()) {
            if (!canRead(ctx.user, profile)) {
                continue;
            }
            var item = ReportTypeItem.newBuilder().setType(profile.type()).setTitle(profile.title())
                    .setService(profile.service()).setSubtype(profile.subtype());
            var latest = latestReadable(ctx.user, service, profile.type());
            if (latest != null) {
                item.setLatest(redact(ctx.user, latest));
            }
            response.addTypes(item);
        }
        observer.complete(response.build());
    }

    private static ReportSnapshot latestReadable(User user, SatelliteReportService collector, String type) {
        Long beforeTime = null;
        String beforeId = null;
        for (;;) {
            var batch = collector.archive().list(type, null, null, beforeTime, beforeId, 25, true);
            for (var snapshot : batch) {
                if (canReadSnapshot(user, snapshot)) {
                    return snapshot;
                }
            }
            if (batch.size() < 25) {
                return null;
            }
            var last = batch.getLast();
            beforeTime = TimeEncoding.fromProtobufTimestamp(last.getSortTime());
            beforeId = last.getId();
        }
    }

    @Override
    public void listReports(Context ctx, ListReportsRequest request, Observer<ListReportsResponse> observer) {
        var service = service(request.getInstance());
        String type = request.hasType() ? request.getType() : null;
        Long start = request.hasStart() ? TimeEncoding.fromProtobufTimestamp(request.getStart()) : null;
        Long stop = request.hasStop() ? TimeEncoding.fromProtobufTimestamp(request.getStop()) : null;
        int limit = request.hasLimit() ? request.getLimit() : 50;
        if (limit < 1 || limit > 200 || (start != null && stop != null && start > stop)) {
            throw new BadRequestException("Invalid report history limit or time range");
        }
        if (type != null && service.profile(type) == null) {
            throw new NotFoundException("Unknown satellite report type");
        }
        var filters = new Filters(request.getInstance(), type, start, stop);
        var before = request.hasNext() ? decodeToken(request.getNext(), filters) : null;

        // Permissions can hide entries within a page, including command-only outcomes.
        var reports = new ArrayList<ReportSnapshot>();
        int batchSize = Math.max(limit + 1, 50);
        while (reports.size() <= limit) {
            var batch = service.archive().list(type, start, stop,
                    before == null ? null : before.time(), before == null ? null : before.id(), batchSize, false);
            for (var snapshot : batch) {
                before = cursor(filters, snapshot);
                if (canReadSnapshot(ctx.user, snapshot) && isVisible(ctx.user, snapshot)) {
                    reports.add(redact(ctx.user, snapshot));
                    if (reports.size() > limit) {
                        break;
                    }
                }
            }
            if (batch.size() < batchSize) {
                break;
            }
        }
        var response = ListReportsResponse.newBuilder();
        if (reports.size() > limit) {
            reports.remove(limit);
            response.setContinuationToken(encodeToken(cursor(filters, reports.get(limit - 1))));
        }
        observer.complete(response.addAllReports(reports).build());
    }

    @Override
    public void getReport(Context ctx, GetReportRequest request, Observer<ReportSnapshot> observer) {
        var service = service(request.getInstance());
        var snapshot = service.archive().get(request.getId());
        if (snapshot == null || !canReadSnapshot(ctx.user, snapshot) || !isVisible(ctx.user, snapshot)) {
            throw new NotFoundException("Unknown satellite report");
        }
        observer.complete(redact(ctx.user, snapshot));
    }

    private static boolean canRead(User user, ReportProfile profile) {
        return profile != null && user.hasObjectPrivilege(ObjectPrivilegeType.ReadPacket, profile.containerName());
    }

    static boolean canReadSnapshot(User user, ReportSnapshot snapshot) {
        return snapshot.hasPacketName() && !snapshot.getPacketName().isEmpty()
                && user.hasObjectPrivilege(ObjectPrivilegeType.ReadPacket, snapshot.getPacketName());
    }

    static boolean isVisible(User user, ReportSnapshot snapshot) {
        return snapshot.hasLastReceived() || (snapshot.hasCommand() && canRead(user, snapshot.getCommand()));
    }

    private static boolean canRead(User user, ReportCommand command) {
        return user.hasObjectPrivilege(ObjectPrivilegeType.CommandHistory, command.getName());
    }

    static ReportSnapshot redact(User user, ReportSnapshot snapshot) {
        var visible = snapshot.toBuilder().clearEcho();
        if (snapshot.hasCommand() && !canRead(user, snapshot.getCommand())) {
            visible.clearCommand().clearRequestedAt().clearResponseDeadline();
        }
        for (var page : visible.getPagesBuilderList()) {
            for (var row : page.getRowsBuilderList()) {
                if (row.hasScheduledCommand() && !canRead(user, row.getScheduledCommand())) {
                    row.clearScheduledCommand();
                }
            }
        }
        return visible.build();
    }

    private static SatelliteReportService service(String name) {
        var instance = YamcsServer.getServer().getInstance(InstancesApi.verifyInstance(name));
        return instance.getServices(SatelliteReportService.class).stream()
                .filter(collector -> collector.archive() != null)
                .findFirst().orElseThrow(() -> new NotFoundException("Satellite reports are unavailable for " + name));
    }

    private static PageToken cursor(Filters filters, ReportSnapshot snapshot) {
        return new PageToken(filters, TimeEncoding.fromProtobufTimestamp(snapshot.getSortTime()), snapshot.getId());
    }

    private static String encodeToken(PageToken token) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(GSON.toJson(token).getBytes(StandardCharsets.UTF_8));
    }

    private static PageToken decodeToken(String value, Filters filters) {
        try {
            var json = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            var token = GSON.fromJson(json, PageToken.class);
            if (token == null || !Objects.equals(filters, token.filters()) || token.id() == null
                    || token.id().isEmpty()) {
                throw new IllegalArgumentException();
            }
            return token;
        } catch (RuntimeException e) {
            throw new BadRequestException("Invalid satellite report continuation token", e);
        }
    }

    private record Filters(String instance, String type, Long start, Long stop) {
    }

    private record PageToken(Filters filters, long time, String id) {
    }
}
