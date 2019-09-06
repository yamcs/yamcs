package org.yamcs.http.api;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.yamcs.StandardTupleDefinitions;
import org.yamcs.YamcsServer;
import org.yamcs.api.HttpBody;
import org.yamcs.api.MediaType;
import org.yamcs.api.Observer;
import org.yamcs.archive.EventRecorder;
import org.yamcs.archive.XtceTmRecorder;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpServer;
import org.yamcs.http.ProtobufRegistry;
import org.yamcs.http.api.RestRequest.IntervalResult;
import org.yamcs.http.api.archive.ArchiveHelper;
import org.yamcs.http.api.archive.RestReplays;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.protobuf.AbstractExportApi;
import org.yamcs.protobuf.ExportEventsRequest;
import org.yamcs.protobuf.ExportPacketsRequest;
import org.yamcs.protobuf.ExportParameterValuesRequest;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ParameterReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.ParameterFormatter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;

import com.csvreader.CsvWriter;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry.ExtensionInfo;

public class ExportApi extends AbstractExportApi<Context> {

    @Override
    public void exportPackets(Context ctx, ExportPacketsRequest request, Observer<HttpBody> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());

        Set<String> nameSet = new HashSet<>(request.getNameList());
        RestHandler.checkObjectPrivileges(ctx.user, ObjectPrivilegeType.ReadPacket, nameSet);

        SqlBuilder sqlb = new SqlBuilder(XtceTmRecorder.TABLE_NAME);

        if (request.hasStart() || request.hasStop()) {
            long start = TimeEncoding.INVALID_INSTANT;
            if (request.hasStart()) {
                start = TimeEncoding.fromProtobufTimestamp(request.getStart());
            }
            long stop = TimeEncoding.INVALID_INSTANT;
            if (request.hasStop()) {
                stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
            }
            IntervalResult ir = new IntervalResult(start, stop);
            sqlb.where(ir.asSqlCondition("gentime"));
        }

        if (request.getNameCount() > 0) {
            sqlb.whereColIn("pname", nameSet);
        }
        String sql = sqlb.toString();

        HttpBody metadata = HttpBody.newBuilder()
                .setContentType(MediaType.OCTET_STREAM.toString())
                .setFilename("packets.raw")
                .build();
        observer.next(metadata);

        RestStreams.stream(instance, sql, sqlb.getQueryArguments(), new StreamSubscriber() {

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                if (observer.isCancelled()) {
                    stream.close();
                    return;
                }

                byte[] raw = (byte[]) tuple.getColumn(StandardTupleDefinitions.TM_PACKET_COLUMN);
                HttpBody body = HttpBody.newBuilder()
                        .setData(ByteString.copyFrom(raw))
                        .build();
                observer.next(body);
            }

            @Override
            public void streamClosed(Stream stream) {
                observer.complete();
            }
        });
    }

    @Override
    public void exportEvents(Context ctx, ExportEventsRequest request, Observer<HttpBody> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        StreamArchiveApi.verifyEventArchiveSupport(instance);
        RestHandler.checkSystemPrivilege(ctx.user, SystemPrivilege.ReadEvents);

        SqlBuilder sqlb = new SqlBuilder(EventRecorder.TABLE_NAME);

        if (request.hasStart() || request.hasStop()) {
            long start = TimeEncoding.INVALID_INSTANT;
            if (request.hasStart()) {
                start = TimeEncoding.fromProtobufTimestamp(request.getStart());
            }
            long stop = TimeEncoding.INVALID_INSTANT;
            if (request.hasStop()) {
                stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
            }
            IntervalResult ir = new IntervalResult(start, stop);
            sqlb.where(ir.asSqlCondition("gentime"));
        }

        if (request.getSourceCount() > 0) {
            sqlb.whereColIn("source", request.getSourceList());
        }

        String severity = "INFO";
        if (request.hasSeverity()) {
            severity = request.getSeverity().toUpperCase();
        }

        switch (severity) {
        case "INFO":
            break;
        case "WATCH":
            sqlb.where("body.severity != 'INFO'");
            break;
        case "WARNING":
            sqlb.whereColIn("body.severity", Arrays.asList("WARNING", "DISTRESS", "CRITICAL", "SEVERE", "ERROR"));
            break;
        case "DISTRESS":
            sqlb.whereColIn("body.severity", Arrays.asList("DISTRESS", "CRITICAL", "SEVERE", "ERROR"));
            break;
        case "CRITICAL":
            sqlb.whereColIn("body.severity", Arrays.asList("CRITICAL", "SEVERE", "ERROR"));
            break;
        case "SEVERE":
            sqlb.whereColIn("body.severity", Arrays.asList("SEVERE", "ERROR"));
            break;
        default:
            sqlb.whereColIn("body.severity = ?", Arrays.asList(severity));
        }
        if (request.hasQ()) {
            sqlb.where("body.message like ?", "%" + request.getQ() + "%");
        }

        String sql = sqlb.toString();

        RestStreams.stream(instance, sql, sqlb.getQueryArguments(), new CsvEventStreamer(observer));
    }

    @Override
    public void exportParameterValues(Context ctx, ExportParameterValuesRequest request, Observer<HttpBody> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());

        ReplayRequest.Builder rr = ReplayRequest.newBuilder().setEndAction(EndAction.QUIT);
        rr.setSpeed(ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP));

        List<NamedObjectId> ids = new ArrayList<>();
        XtceDb mdb = XtceDbFactory.getInstance(instance);
        String namespace = null;

        if (request.hasStart()) {
            rr.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));
        }
        if (request.hasStop()) {
            rr.setStop(TimeEncoding.fromProtobufTimestamp(request.getStop()));
        }
        for (String id : request.getParametersList()) {
            Parameter p = mdb.getParameter(id);
            if (p == null) {
                throw new BadRequestException("Invalid parameter name specified " + id);
            }

            RestHandler.checkObjectPrivileges(ctx.user, ObjectPrivilegeType.ReadParameter, p.getQualifiedName());
            ids.add(XtceDb.toNamedObjectId(id));
        }
        if (request.hasNamespace()) {
            namespace = request.getNamespace();
        }

        if (ids.isEmpty()) {
            for (Parameter p : mdb.getParameters()) {
                if (!RestHandler.hasObjectPrivilege(ctx.user, ObjectPrivilegeType.ReadParameter,
                        p.getQualifiedName())) {
                    continue;
                }
                if (namespace != null) {
                    String alias = p.getAlias(namespace);
                    if (alias != null) {
                        ids.add(NamedObjectId.newBuilder().setNamespace(namespace).setName(alias).build());
                    }
                } else {
                    ids.add(NamedObjectId.newBuilder().setName(p.getQualifiedName()).build());
                }
            }
        }
        rr.setParameterRequest(ParameterReplayRequest.newBuilder().addAllNameFilter(ids));

        String filename = "parameter-data";

        boolean addRaw = false;
        boolean addMonitoring = false;
        for (String extra : request.getExtraList()) {
            if (extra.equals("raw")) {
                addRaw = true;
            } else if (extra.equals("monitoring")) {
                addMonitoring = true;
            } else {
                throw new BadRequestException("Unexpected option for parameter 'extra': " + extra);
            }
        }
        RestParameterReplayListener l = new CsvParameterStreamer(
                observer, filename, ids, addRaw, addMonitoring);
        observer.setCancelHandler(l::requestReplayAbortion);
        RestReplays.replay(instance, ctx.user, rr.build(), l);
    }

    private static class CsvEventStreamer implements StreamSubscriber {

        Observer<HttpBody> observer;
        ProtobufRegistry protobufRegistry;

        CsvEventStreamer(Observer<HttpBody> observer) {
            this.observer = observer;

            YamcsServer yamcs = YamcsServer.getServer();
            List<HttpServer> services = yamcs.getGlobalServices(HttpServer.class);
            protobufRegistry = services.get(0).getProtobufRegistry();

            List<ExtensionInfo> extensionFields = protobufRegistry.getExtensions(Event.getDescriptor());
            String[] rec = new String[5 + extensionFields.size()];
            int i = 0;
            rec[i++] = "Source";
            rec[i++] = "Generation Time";
            rec[i++] = "Reception Time";
            rec[i++] = "Event Type";
            rec[i++] = "Event Text";
            for (ExtensionInfo extension : extensionFields) {
                rec[i++] = "" + extension.descriptor.getName();
            }

            HttpBody metadata = HttpBody.newBuilder()
                    .setContentType(MediaType.CSV.toString())
                    .setFilename("events.csv")
                    .setData(toByteString(rec))
                    .build();

            observer.next(metadata);
        }

        @Override
        public void onTuple(Stream stream, Tuple tuple) {
            if (observer.isCancelled()) {
                stream.close();
                return;
            }

            Event event = ArchiveHelper.tupleToEvent(tuple, protobufRegistry);

            List<ExtensionInfo> extensionFields = protobufRegistry.getExtensions(Event.getDescriptor());

            String[] rec = new String[5 + extensionFields.size()];
            int i = 0;
            rec[i++] = event.getSource();
            rec[i++] = event.getGenerationTimeUTC();
            rec[i++] = event.getReceptionTimeUTC();
            rec[i++] = event.getType();
            rec[i++] = event.getMessage();
            for (ExtensionInfo extension : extensionFields) {
                rec[i++] = "" + event.getField(extension.descriptor);
            }

            HttpBody body = HttpBody.newBuilder()
                    .setData(toByteString(rec))
                    .build();
            observer.next(body);
        }

        private ByteString toByteString(String[] rec) {
            ByteString.Output bout = ByteString.newOutput();
            CsvWriter writer = new CsvWriter(bout, '\t', StandardCharsets.UTF_8);
            try {
                writer.writeRecord(rec);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                writer.close();
            }

            return bout.toByteString();
        }

        @Override
        public void streamClosed(Stream stream) {
            observer.complete();
        }
    }

    private static class CsvParameterStreamer extends RestParameterReplayListener {

        Observer<HttpBody> observer;
        List<NamedObjectId> ids;
        boolean addRaw;
        boolean addMonitoring;
        int recordCount = 0;

        CsvParameterStreamer(Observer<HttpBody> observer, String filename, List<NamedObjectId> ids,
                boolean addRaw, boolean addMonitoring) {
            this.observer = observer;
            this.ids = ids;
            this.addRaw = addRaw;
            this.addMonitoring = addMonitoring;

            HttpBody metadata = HttpBody.newBuilder()
                    .setContentType(MediaType.CSV.toString())
                    .setFilename(filename)
                    .build();

            observer.next(metadata);
        }

        @Override
        protected void onParameterData(List<ParameterValueWithId> params) {
            List<ParameterValue> pvals = new ArrayList<>();
            for (ParameterValueWithId pvalid : params) {
                pvals.add(pvalid.toGbpParameterValue());
            }

            ByteString.Output data = ByteString.newOutput();
            try (Writer writer = new OutputStreamWriter(data, StandardCharsets.UTF_8);
                    ParameterFormatter formatter = new ParameterFormatter(writer, ids, '\t')) {
                formatter.setWriteHeader(recordCount == 0);
                formatter.setPrintRaw(addRaw);
                formatter.setPrintMonitoring(addMonitoring);
                formatter.writeParameters(pvals);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            HttpBody body = HttpBody.newBuilder()
                    .setData(data.toByteString())
                    .build();
            observer.next(body);
            recordCount++;
        }

        @Override
        public void replayFailed(Throwable t) {
            observer.completeExceptionally(t);
        }

        @Override
        public void replayFinished() {
            observer.complete();
        }
    }
}
