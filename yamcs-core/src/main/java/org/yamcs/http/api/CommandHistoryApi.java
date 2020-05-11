package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yamcs.Processor;
import org.yamcs.api.Observer;
import org.yamcs.archive.CommandHistoryRecorder;
import org.yamcs.archive.GPBHelper;
import org.yamcs.cmdhistory.CommandHistoryConsumer;
import org.yamcs.cmdhistory.CommandHistoryFilter;
import org.yamcs.cmdhistory.CommandHistoryRequestManager;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.AbstractCommandHistoryApi;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.GetCommandRequest;
import org.yamcs.protobuf.ListCommandsRequest;
import org.yamcs.protobuf.ListCommandsResponse;
import org.yamcs.protobuf.StreamCommandsRequest;
import org.yamcs.protobuf.SubscribeCommandsRequest;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.gson.Gson;

public class CommandHistoryApi extends AbstractCommandHistoryApi<Context> {

    private static final Pattern PATTERN_COMMAND_ID = Pattern.compile("([0-9]+)(-(.*))?-([0-9]+)");

    @Override
    public void listCommands(Context ctx, ListCommandsRequest request, Observer<ListCommandsResponse> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        if (ydb.getTable(CommandHistoryRecorder.TABLE_NAME) == null) {
            observer.complete(ListCommandsResponse.getDefaultInstance());
            return;
        }

        long pos = request.hasPos() ? request.getPos() : 0;
        int limit = request.hasLimit() ? request.getLimit() : 100;
        boolean desc = !request.getOrder().equals("asc");

        CommandPageToken nextToken = null;
        if (request.hasNext()) {
            String next = request.getNext();
            nextToken = CommandPageToken.decode(next);
        }

        SqlBuilder sqlb = new SqlBuilder(CommandHistoryRecorder.TABLE_NAME);

        if (request.hasStart()) {
            sqlb.whereColAfterOrEqual("gentime", request.getStart());
        }
        if (request.hasStop()) {
            sqlb.whereColBefore("gentime", request.getStop());
        }

        if (request.hasQ()) {
            sqlb.where("cmdName like ?", "%" + request.getQ() + "%");
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
        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

            CommandHistoryEntry last;
            int count;

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                CommandHistoryEntry entry = GPBHelper.tupleToCommandHistoryEntry(tuple);
                if (ctx.user.hasObjectPrivilege(ObjectPrivilegeType.CommandHistory,
                        entry.getCommandId().getCommandName())) {
                    count++;
                    if (count <= limit) {
                        responseb.addEntry(entry);
                        last = entry;
                    }
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
                observer.complete(responseb.build());
            }
        });
    }

    @Override
    public void getCommand(Context ctx, GetCommandRequest request, Observer<CommandHistoryEntry> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        Matcher matcher = PATTERN_COMMAND_ID.matcher(request.getId());
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
        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                CommandHistoryEntry che = GPBHelper.tupleToCommandHistoryEntry(tuple);
                commands.add(che);
            }

            @Override
            public void streamClosed(Stream stream) {
                if (commands.isEmpty()) {
                    observer.completeExceptionally(new NotFoundException());
                } else if (commands.size() > 1) {
                    observer.completeExceptionally(new InternalServerErrorException("Too many results"));
                } else {
                    CommandHistoryEntry entry = commands.get(0);
                    ctx.checkObjectPrivileges(ObjectPrivilegeType.CommandHistory,
                            entry.getCommandId().getCommandName());
                    observer.complete(commands.get(0));
                }
            }
        });
    }

    @Override
    public void subscribeCommands(Context ctx, SubscribeCommandsRequest request,
            Observer<CommandHistoryEntry> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        Processor processor = ProcessingApi.verifyProcessor(instance, request.getProcessor());
        if (!processor.hasCommanding() || processor.getCommandHistoryManager() == null) {
            return; // No Error, just send no data
        }

        CommandHistoryRequestManager requestManager = processor.getCommandHistoryManager();
        boolean ignorePastCommands = true;
        if (request.hasIgnorePastCommands()) {
            ignorePastCommands = request.getIgnorePastCommands();
        }

        long since = ignorePastCommands ? processor.getCurrentTime() : 0;
        CommandHistoryConsumer listener = new CommandHistoryConsumer() {

            @Override
            public void addedCommand(PreparedCommand pc) {
                CommandHistoryEntry entry = CommandHistoryEntry.newBuilder().setCommandId(pc.getCommandId())
                        .setGenerationTimeUTC(TimeEncoding.toString(pc.getCommandId().getGenerationTime()))
                        .addAllAttr(pc.getAttributes())
                        .build();
                observer.next(entry);
            }

            @Override
            public void updatedCommand(CommandId cmdId, long changeDate, String key, Value value) {
                CommandHistoryAttribute cha = CommandHistoryAttribute.newBuilder()
                        .setName(key)
                        .setValue(ValueUtility.toGbp(value))
                        .build();
                CommandHistoryEntry entry = CommandHistoryEntry.newBuilder()
                        .setGenerationTimeUTC(TimeEncoding.toString(cmdId.getGenerationTime()))
                        .setCommandId(cmdId)
                        .addAttr(cha)
                        .build();
                observer.next(entry);
            }
        };
        CommandHistoryFilter subscription = requestManager.subscribeCommandHistory(null, since, listener);
        observer.setCancelHandler(() -> requestManager.unsubscribeCommandHistory(subscription.subscriptionId));
    }

    @Override
    public void streamCommands(Context ctx, StreamCommandsRequest request, Observer<CommandHistoryEntry> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        // Quick-check in case the user is specific
        ctx.checkObjectPrivileges(ObjectPrivilegeType.CommandHistory, request.getNameList());

        SqlBuilder sqlb = new SqlBuilder(CommandHistoryRecorder.TABLE_NAME);

        if (request.hasStart()) {
            sqlb.whereColAfterOrEqual("gentime", request.getStart());
        }
        if (request.hasStop()) {
            sqlb.whereColBefore("gentime", request.getStop());
        }

        if (request.getNameCount() > 0) {
            sqlb.whereColIn("cmdName", request.getNameList());
        }

        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                CommandHistoryEntry entry = GPBHelper.tupleToCommandHistoryEntry(tuple);
                if (ctx.user.hasObjectPrivilege(ObjectPrivilegeType.CommandHistory,
                        entry.getCommandId().getCommandName())) {
                    observer.next(entry);
                }
            }

            @Override
            public void streamClosed(Stream stream) {
                observer.complete();
            }
        });
    }

    /**
     * Stateless continuation token for paged requests on the cmdhist table
     */
    private static class CommandPageToken {

        long gentime;
        String origin;
        int seqNum;

        CommandPageToken(long gentime, String origin, int seqNum) {
            this.gentime = gentime;
            this.origin = origin;
            this.seqNum = seqNum;
        }

        static CommandPageToken decode(String encoded) {
            String decoded = new String(Base64.getUrlDecoder().decode(encoded));
            return new Gson().fromJson(decoded, CommandPageToken.class);
        }

        String encodeAsString() {
            String json = new Gson().toJson(this);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
        }
    }
}
