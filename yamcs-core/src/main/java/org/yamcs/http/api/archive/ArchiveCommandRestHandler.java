package org.yamcs.http.api.archive;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yamcs.archive.CommandHistoryRecorder;
import org.yamcs.archive.GPBHelper;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.api.RestHandler;
import org.yamcs.http.api.RestRequest;
import org.yamcs.http.api.RestRequest.IntervalResult;
import org.yamcs.http.api.RestStreams;
import org.yamcs.http.api.Route;
import org.yamcs.http.api.SqlBuilder;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.ListCommandsResponse;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class ArchiveCommandRestHandler extends RestHandler {

    private static final Pattern PATTERN_COMMAND_ID = Pattern.compile("([0-9]+)(-(.*))?-([0-9]+)");

    @Route(path = "/api/archive/{instance}/commands")
    public void listCommands(RestRequest req) throws HttpException {
        String instance = verifyInstance(req.getRouteParam("instance"));

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        if (ydb.getTable(CommandHistoryRecorder.TABLE_NAME) == null) {
            completeOK(req, ListCommandsResponse.newBuilder().build());
            return;
        }

        long pos = req.getQueryParameterAsLong("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);
        boolean desc = req.asksDescending(true);

        EventPageToken nextToken = null;
        if (req.hasQueryParameter("next")) {
            String next = req.getQueryParameter("next");
            nextToken = EventPageToken.decode(next);
        }

        SqlBuilder sqlb = new SqlBuilder(CommandHistoryRecorder.TABLE_NAME);
        IntervalResult ir = req.scanForInterval();
        if (ir.hasInterval()) {
            sqlb.where(ir.asSqlCondition("gentime"));
        }
        if (req.hasQueryParameter("q")) {
            sqlb.where("cmdName like ?", "%" + req.getQueryParameter("q") + "%");
        }
        if (nextToken != null) {
            // TODO this currently ignores the origin column (also part of the key)
            // Requires string comparison in StreamSQL, and an even more complicated query condition...
            if (desc) {
                sqlb.where("(gentime < ? or (gentime = ? and seqNum < ?))",
                        nextToken.gentime, nextToken.gentime, nextToken.seqNum);
            } else {
                sqlb.where("(gentime > ? or (gentime = ? and seqNum > ?))",
                        nextToken.gentime, nextToken.gentime, nextToken.seqNum);
            }
        }

        sqlb.descend(desc);
        sqlb.limit(pos, limit + 1); // one more to detect hasMore

        ListCommandsResponse.Builder responseb = ListCommandsResponse.newBuilder();
        RestStreams.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

            CommandHistoryEntry last;
            int count;

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                if (++count <= limit) {
                    CommandHistoryEntry che = GPBHelper.tupleToCommandHistoryEntry(tuple);
                    responseb.addEntry(che);
                    last = che;
                }
            }

            @Override
            public void streamClosed(Stream stream) {
                if (count > limit) {
                    CommandId cmdId = last.getCommandId();
                    CommandPageToken token = new CommandPageToken(
                            cmdId.getGenerationTime(), cmdId.getOrigin(),
                            cmdId.getSequenceNumber());
                    responseb.setContinuationToken(token.encodeAsString());
                }
                completeOK(req, responseb.build());
            }
        });
    }

    @Route(path = "/api/archive/{instance}/commands/{id}")
    public void getCommand(RestRequest req) throws HttpException {
        String instance = verifyInstance(req.getRouteParam("instance"));

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        if (ydb.getTable(CommandHistoryRecorder.TABLE_NAME) == null) {
            completeOK(req, ListCommandsResponse.newBuilder().build());
            return;
        }

        String id = req.getRouteParam("id");
        Matcher matcher = PATTERN_COMMAND_ID.matcher(id);
        if (!matcher.matches()) {
            throw new BadRequestException("Invalid command id");
        }

        long gentime = Long.parseLong(matcher.group(1));
        String origin = matcher.group(3) != null ? matcher.group(3) : "";
        int seqNum = Integer.parseInt(matcher.group(4));

        SqlBuilder sqlb = new SqlBuilder(CommandHistoryRecorder.TABLE_NAME)
                .where("gentime = ?", gentime)
                .where("seqNum = ?", seqNum)
                .where("origin = ?", origin);

        List<CommandHistoryEntry> commands = new ArrayList<>();
        RestStreams.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                CommandHistoryEntry che = GPBHelper.tupleToCommandHistoryEntry(tuple);
                commands.add(che);
            }

            @Override
            public void streamClosed(Stream stream) {
                if (commands.isEmpty()) {
                    throw new NotFoundException();
                } else if (commands.size() > 1) {
                    throw new InternalServerErrorException("Too many results");
                } else {
                    completeOK(req, commands.get(0));
                }
            }
        });
    }
}
