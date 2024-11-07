package org.yamcs.http.api;

import static org.yamcs.cmdhistory.CommandHistoryPublisher.SUFFIX_RETURN;
import static org.yamcs.cmdhistory.CommandHistoryPublisher.SUFFIX_STATUS;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.yamcs.ErrorInCommand;
import org.yamcs.NoPermissionException;
import org.yamcs.Processor;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.api.HttpBody;
import org.yamcs.api.Observer;
import org.yamcs.archive.CommandHistoryRecorder;
import org.yamcs.archive.GPBHelper;
import org.yamcs.cmdhistory.Attribute;
import org.yamcs.cmdhistory.CommandHistoryConsumer;
import org.yamcs.cmdhistory.CommandHistoryFilter;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryRequestManager;
import org.yamcs.cmdhistory.protobuf.Cmdhistory.AssignmentInfo;
import org.yamcs.commanding.CommandQueue;
import org.yamcs.commanding.CommandQueueManager;
import org.yamcs.commanding.CommandingManager;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.MediaType;
import org.yamcs.http.NotFoundException;
import org.yamcs.logging.Log;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.protobuf.AbstractCommandsApi;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.ExportCommandRequest;
import org.yamcs.protobuf.ExportCommandsRequest;
import org.yamcs.protobuf.GetCommandRequest;
import org.yamcs.protobuf.IssueCommandRequest;
import org.yamcs.protobuf.IssueCommandResponse;
import org.yamcs.protobuf.ListCommandsRequest;
import org.yamcs.protobuf.ListCommandsResponse;
import org.yamcs.protobuf.StreamCommandsRequest;
import org.yamcs.protobuf.SubscribeCommandsRequest;
import org.yamcs.protobuf.UpdateCommandHistoryRequest;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.Significance.Levels;
import org.yamcs.yarch.SqlBuilder;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yaml.snakeyaml.util.UriEncoder;

import com.csvreader.CsvWriter;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.util.Timestamps;

public class CommandsApi extends AbstractCommandsApi<Context> {

    private static final Pattern PATTERN_COMMAND_ID = Pattern.compile("([0-9]+)(-(.*))?-([0-9]+)");
    private static final Log log = new Log(CommandsApi.class);

    @Override
    public void issueCommand(Context ctx, IssueCommandRequest request, Observer<IssueCommandResponse> observer) {
        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        if (!processor.hasCommanding()) {
            throw new BadRequestException("Commanding not activated for this processor");
        }

        String requestCommandName = UriEncoder.decode(request.getName());
        Mdb mdb = MdbFactory.getInstance(processor.getInstance());
        MetaCommand cmd = MdbApi.verifyCommand(mdb, requestCommandName);

        ctx.checkObjectPrivileges(ObjectPrivilegeType.Command, cmd.getQualifiedName());

        String origin = ctx.getClientAddress();
        int sequenceNumber = 0;
        boolean dryRun = false;
        String comment = null;

        if (request.hasOrigin()) { // TODO remove this override?
            origin = request.getOrigin();
        }
        if (request.hasDryRun()) {
            dryRun = request.getDryRun();
        }
        if (request.hasSequenceNumber()) {
            sequenceNumber = request.getSequenceNumber();
        }
        if (request.hasComment()) {
            comment = request.getComment();
        }

        var args = GpbWellKnownHelper.toJava(request.getArgs());

        PreparedCommand preparedCommand;
        try {
            preparedCommand = processor.getCommandingManager().buildCommand(cmd, args, origin, sequenceNumber,
                    ctx.user);
            if (comment != null && !comment.trim().isEmpty()) {
                preparedCommand.setComment(comment);
            }

            if (request.getExtraCount() > 0) {
                ctx.checkSystemPrivilege(SystemPrivilege.CommandOptions);
                request.getExtraMap().forEach((k, v) -> {
                    var commandOption = YamcsServer.getServer().getCommandOption(k);
                    if (commandOption == null) {
                        throw new BadRequestException("Unknown command option '" + k + "'");
                    }
                    preparedCommand.addAttribute(CommandHistoryAttribute.newBuilder()
                            .setName(k)
                            .setValue(commandOption.coerceValue(v))
                            .build());
                });
            }

            if (request.hasDisableVerifiers()) {
                ctx.checkSystemPrivilege(SystemPrivilege.CommandOptions);
                preparedCommand.disableCommandVerifiers(request.getDisableVerifiers());
            }

            if (request.hasStream()) {
                ctx.checkSystemPrivilege(SystemPrivilege.CommandOptions);
                var ydb = YarchDatabase.getInstance(request.getInstance());
                var tcStream = TableApi.verifyStream(ctx, ydb, request.getStream());
                preparedCommand.setTcStream(tcStream);
            }

            if (request.hasDisableTransmissionConstraints()) {
                ctx.checkSystemPrivilege(SystemPrivilege.CommandOptions);
                preparedCommand.disableTransmissionConstraints(request.getDisableTransmissionConstraints());
            } else if (request.getVerifierConfigCount() > 0) {
                ctx.checkSystemPrivilege(SystemPrivilege.CommandOptions);
                List<String> invalidVerifiers = new ArrayList<>();
                for (String stage : request.getVerifierConfigMap().keySet()) {
                    if (!hasVerifier(cmd, stage)) {
                        invalidVerifiers.add(stage);
                    }
                }
                if (!invalidVerifiers.isEmpty()) {
                    throw new BadRequestException(
                            "The command does not have the following verifiers: " + invalidVerifiers.toString());
                }

                request.getVerifierConfigMap().forEach((k, v) -> {
                    preparedCommand.addVerifierConfig(k, v);
                });
            }
        } catch (NoPermissionException e) {
            throw new ForbiddenException(e);
        } catch (ErrorInCommand e) {
            throw new BadRequestException(e);
        } catch (YamcsException e) { // could be anything, consider as internal server error
            throw new InternalServerErrorException(e);
        }

        if (!dryRun && processor.getConfig().checkCommandClearance()) {
            if (ctx.user.getClearance() == null) {
                throw new ForbiddenException("Not cleared for commanding");
            }
            Levels clearance = Levels.valueOf(ctx.user.getClearance().getLevel().toUpperCase());
            Levels level = null;
            if (preparedCommand.getMetaCommand().getEffectiveDefaultSignificance() != null) {
                level = preparedCommand.getMetaCommand().getEffectiveDefaultSignificance().getConsequenceLevel();
            }
            if (level != null && level.isMoreSevere(clearance)) {
                throw new ForbiddenException("Not cleared for this level of commands");
            }
        }

        // Good, now send
        CommandQueue queue;
        if (dryRun) {
            CommandQueueManager mgr = processor.getCommandingManager().getCommandQueueManager();
            queue = mgr.getQueue(ctx.user, preparedCommand);
        } else {
            queue = processor.getCommandingManager().sendCommand(ctx.user, preparedCommand);
        }

        var commandName = preparedCommand.getMetaCommand().getQualifiedName();

        var responseb = IssueCommandResponse.newBuilder()
                .setId(toStringIdentifier(preparedCommand.getCommandId()))
                .setGenerationTime(TimeEncoding.toProtobufTimestamp(preparedCommand.getGenerationTime()))
                .setOrigin(preparedCommand.getCommandId().getOrigin())
                .setSequenceNumber(preparedCommand.getCommandId().getSequenceNumber())
                .setCommandName(commandName)
                .setUsername(preparedCommand.getUsername())
                .addAllAssignments(preparedCommand.getAssignments());

        // Best effort, not a problem if the command no longer exists
        var command = mdb.getMetaCommand(commandName);
        if (command != null && command.getAliasSet() != null) {
            var aliasSet = command.getAliasSet();
            responseb.putAllAliases(aliasSet.getAliases());
        }

        byte[] unprocessedBinary = preparedCommand.getUnprocessedBinary();
        if (unprocessedBinary != null) {
            responseb.setUnprocessedBinary(ByteString.copyFrom(unprocessedBinary));
        }

        byte[] binary = preparedCommand.getBinary();
        if (binary != null) {
            responseb.setBinary(ByteString.copyFrom(binary));
        }

        if (queue != null) {
            responseb.setQueue(queue.getName());
        }

        observer.complete(responseb.build());
    }

    private boolean hasVerifier(MetaCommand cmd, String stage) {
        boolean hasVerifier = cmd.getCommandVerifiers().stream().anyMatch(cv -> cv.getStage().equals(stage));
        if (hasVerifier) {
            return true;
        } else {
            MetaCommand parent = cmd.getBaseMetaCommand();
            if (parent == null) {
                return false;
            } else {
                return hasVerifier(parent, stage);
            }
        }
    }

    @Override
    public void updateCommandHistory(Context ctx, UpdateCommandHistoryRequest request, Observer<Empty> observer) {
        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        if (!processor.hasCommanding()) {
            throw new BadRequestException("Commanding not activated for this processor");
        }
        if (!ctx.user.hasSystemPrivilege(SystemPrivilege.ModifyCommandHistory)) {
            throw new ForbiddenException("User has no privilege to update command history");
        }

        CommandId cmdId = fromStringIdentifier(request.getName(), request.getId());
        CommandingManager manager = processor.getCommandingManager();
        for (CommandHistoryAttribute attr : request.getAttributesList()) {
            manager.setCommandAttribute(cmdId, attr);
        }

        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void listCommands(Context ctx, ListCommandsRequest request, Observer<ListCommandsResponse> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        Mdb mdb = MdbFactory.getInstance(instance);

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        if (ydb.getTable(CommandHistoryRecorder.TABLE_NAME) == null) {
            observer.complete(ListCommandsResponse.getDefaultInstance());
            return;
        }

        Long pos = request.hasPos() ? request.getPos() : null;
        int limit = request.hasLimit() ? request.getLimit() : 100;
        boolean desc = !request.getOrder().equals("asc");

        if (pos != null) {
            log.warn("DEPRECATION WARNING: Do not use pos, use continuationToken instead");
        }

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
        if (request.hasQueue()) {
            sqlb.where("queue = ?", request.getQueue());
        }
        NameDescriptionSearchMatcher matcher = null;
        if (request.hasQ()) {
            matcher = new NameDescriptionSearchMatcher(request.getQ());
            matcher.setSearchDescription(false);
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

        if (pos != null) {
            sqlb.limit(pos, limit + 1l); // one more to detect hasMore
        }

        var finalMatcher = matcher;
        ListCommandsResponse.Builder responseb = ListCommandsResponse.newBuilder();
        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

            CommandHistoryEntry last;
            int count;

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                CommandHistoryEntry entry = GPBHelper.tupleToCommandHistoryEntry(tuple, mdb);
                if (finalMatcher != null) {
                    var command = mdb.getMetaCommand(entry.getCommandName());
                    if (command != null && !finalMatcher.matches(command)) {
                        return;
                    } else if (command == null && !finalMatcher.matches(entry.getCommandName())) {
                        // Command could have been renamed, match only on the stored name.
                        return;
                    }
                }
                if (ctx.user.hasObjectPrivilege(ObjectPrivilegeType.CommandHistory,
                        entry.getCommandName())) {
                    count++;
                    if (count <= limit) {
                        responseb.addCommands(entry);
                        responseb.addEntry(entry);
                        last = entry;
                    } else {
                        stream.close();
                    }
                }
            }

            @Override
            public void streamClosed(Stream stream) {
                if (count > limit) {
                    CommandId cmdId = last.getCommandId();
                    CommandPageToken token = new CommandPageToken(
                            cmdId.getGenerationTime(), last.getOrigin(),
                            last.getSequenceNumber());
                    responseb.setContinuationToken(token.encodeAsString());
                }
                observer.complete(responseb.build());
            }
        });
    }

    @Override
    public void getCommand(Context ctx, GetCommandRequest request, Observer<CommandHistoryEntry> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        Mdb mdb = MdbFactory.getInstance(instance);

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
                CommandHistoryEntry command = GPBHelper.tupleToCommandHistoryEntry(tuple, mdb);
                commands.add(command);
            }

            @Override
            public void streamClosed(Stream stream) {
                if (commands.isEmpty()) {
                    observer.completeExceptionally(new NotFoundException());
                } else if (commands.size() > 1) {
                    observer.completeExceptionally(new InternalServerErrorException("Too many results"));
                } else {
                    CommandHistoryEntry command = commands.get(0);
                    ctx.checkObjectPrivileges(ObjectPrivilegeType.CommandHistory, command.getCommandName());
                    observer.complete(command);
                }
            }
        });
    }

    @Override
    public void exportCommand(Context ctx, ExportCommandRequest request, Observer<HttpBody> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        Mdb mdb = MdbFactory.getInstance(instance);

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
                CommandHistoryEntry command = GPBHelper.tupleToCommandHistoryEntry(tuple, mdb);
                commands.add(command);
            }

            @Override
            public void streamClosed(Stream stream) {
                if (commands.isEmpty()) {
                    observer.completeExceptionally(new NotFoundException());
                } else if (commands.size() > 1) {
                    observer.completeExceptionally(new InternalServerErrorException("Too many results"));
                } else {
                    CommandHistoryEntry command = commands.get(0);
                    ctx.checkObjectPrivileges(ObjectPrivilegeType.CommandHistory, command.getCommandName());

                    String timestamp = DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now()
                            .truncatedTo(ChronoUnit.MILLIS))
                            .replace("-", "")
                            .replace(":", "")
                            .replace(".", "");
                    HttpBody.Builder responseb = HttpBody.newBuilder()
                            .setFilename("command-" + timestamp + "-" + seqNum + ".raw")
                            .setContentType(MediaType.OCTET_STREAM.toString());
                    for (CommandHistoryAttribute attr : command.getAttrList()) {
                        if (attr.getName().equals(PreparedCommand.CNAME_BINARY)) {
                            responseb.setData(attr.getValue().getBinaryValue());
                        }
                    }
                    observer.complete(responseb.build());
                }
            }
        });
    }

    @Override
    public void subscribeCommands(Context ctx, SubscribeCommandsRequest request,
            Observer<CommandHistoryEntry> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
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
                if (ctx.user.hasObjectPrivilege(ObjectPrivilegeType.CommandHistory, pc.getCommandName())) {
                    var entryb = CommandHistoryEntry.newBuilder()
                            .setId(pc.getId())
                            .setOrigin(pc.getOrigin())
                            .setCommandName(pc.getCommandName())
                            .setSequenceNumber(pc.getSequenceNumber())
                            .setCommandId(pc.getCommandId())
                            .setGenerationTime(TimeEncoding.toProtobufTimestamp(pc.getCommandId().getGenerationTime()))
                            .addAllAssignments(pc.getAssignments());

                    // add a string value for the timestamps
                    // external clients (python, web) cannot work with Yamcs times
                    pc.getAttributes().forEach(a -> {
                        var v = a.getValue();
                        if (v.getType() == Value.Type.TIMESTAMP) {
                            var v1 = v.toBuilder().setStringValue(TimeEncoding.toString(v.getTimestampValue()));
                            a = a.toBuilder().setValue(v1).build();
                        }
                        entryb.addAttr(a);
                    });
                    var aliasSet = pc.getMetaCommand().getAliasSet();
                    if (aliasSet != null) {
                        for (var alias : aliasSet.getAliases().entrySet()) {
                            entryb.putAliases(alias.getKey(), alias.getValue());
                        }
                    }
                    observer.next(entryb.build());
                }
            }

            @Override
            public void updatedCommand(CommandId cmdId, long changeDate, List<Attribute> attrs) {
                if (ctx.user.hasObjectPrivilege(ObjectPrivilegeType.CommandHistory, cmdId.getCommandName())) {
                    CommandHistoryEntry.Builder entry = CommandHistoryEntry.newBuilder()
                            .setId(cmdId.getGenerationTime() + "-" + cmdId.getOrigin() + "-"
                                    + cmdId.getSequenceNumber())
                            .setOrigin(cmdId.getOrigin())
                            .setCommandName(cmdId.getCommandName())
                            .setGenerationTime(TimeEncoding.toProtobufTimestamp(cmdId.getGenerationTime()))
                            .setCommandId(cmdId);
                    for (Attribute a : attrs) {
                        CommandHistoryAttribute cha = CommandHistoryAttribute.newBuilder()
                                .setName(a.getKey())
                                .setValue(ValueUtility.toGbp(a.getValue()))
                                .build();
                        entry.addAttr(cha);
                    }
                    observer.next(entry.build());
                }
            }
        };
        CommandHistoryFilter subscription = requestManager.subscribeCommandHistory(null, since, listener);
        observer.setCancelHandler(() -> requestManager.unsubscribeCommandHistory(subscription.subscriptionId));
    }

    @Override
    public void streamCommands(Context ctx, StreamCommandsRequest request, Observer<CommandHistoryEntry> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        Mdb mdb = MdbFactory.getInstance(instance);

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
                CommandHistoryEntry entry = GPBHelper.tupleToCommandHistoryEntry(tuple, mdb);
                if (ctx.user.hasObjectPrivilege(ObjectPrivilegeType.CommandHistory, entry.getCommandName())) {
                    observer.next(entry);
                }
            }

            @Override
            public void streamClosed(Stream stream) {
                observer.complete();
            }
        });
    }

    @Override
    public void exportCommands(Context ctx, ExportCommandsRequest request, Observer<HttpBody> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        Mdb mdb = MdbFactory.getInstance(instance);

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

        String sql = sqlb.toString();

        char delimiter = '\t';
        if (request.hasDelimiter()) {
            switch (request.getDelimiter()) {
            case "TAB":
                delimiter = '\t';
                break;
            case "SEMICOLON":
                delimiter = ';';
                break;
            case "COMMA":
                delimiter = ',';
                break;
            default:
                throw new BadRequestException("Unexpected column delimiter");
            }
        }

        CsvCommandStreamer streamer = new CsvCommandStreamer(ctx, observer, delimiter, mdb);
        StreamFactory.stream(instance, sql, sqlb.getQueryArguments(), streamer);
    }

    private static CommandId fromStringIdentifier(String commandName, String id) {
        CommandId.Builder b = CommandId.newBuilder();
        b.setCommandName(commandName);
        int firstDash = id.indexOf('-');
        long generationTime = Long.parseLong(id.substring(0, firstDash));
        b.setGenerationTime(generationTime);
        int lastDash = id.lastIndexOf('-');
        int sequenceNumber = Integer.parseInt(id.substring(lastDash + 1));
        b.setSequenceNumber(sequenceNumber);
        if (firstDash != lastDash) {
            String origin = id.substring(firstDash + 1, lastDash);
            b.setOrigin(origin);
        } else {
            b.setOrigin("");
        }

        return b.build();
    }

    private static String toStringIdentifier(CommandId commandId) {
        String id = commandId.getGenerationTime() + "-";
        if (commandId.hasOrigin() && !"".equals(commandId.getOrigin())) {
            id += commandId.getOrigin() + "-";
        }
        return id + commandId.getSequenceNumber();
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

    private static class CsvCommandStreamer implements StreamSubscriber {

        Context ctx;
        Observer<HttpBody> observer;
        char columnDelimiter;
        Mdb mdb;

        CsvCommandStreamer(Context ctx, Observer<HttpBody> observer, char columnDelimiter, Mdb mdb) {
            this.ctx = ctx;
            this.observer = observer;
            this.columnDelimiter = columnDelimiter;
            this.mdb = mdb;

            String[] rec = new String[13];
            int i = 0;
            rec[i++] = "Generation Time";
            rec[i++] = "Command Name";
            rec[i++] = "Arguments";
            rec[i++] = "Origin";
            rec[i++] = "Sequence Number";
            rec[i++] = "Username";
            rec[i++] = "Queue";
            rec[i++] = "Binary";
            rec[i++] = CommandHistoryPublisher.AcknowledgeQueued_KEY;
            rec[i++] = CommandHistoryPublisher.AcknowledgeReleased_KEY;
            rec[i++] = CommandHistoryPublisher.AcknowledgeSent_KEY;
            rec[i++] = "Completion";
            rec[i++] = "Return Value";

            String dateString = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String filename = "command_export_" + dateString + ".csv";

            HttpBody metadata = HttpBody.newBuilder()
                    .setContentType(MediaType.CSV.toString())
                    .setFilename(filename)
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

            var command = GPBHelper.tupleToCommandHistoryEntry(tuple, mdb);
            if (!ctx.user.hasObjectPrivilege(ObjectPrivilegeType.CommandHistory, command.getCommandName())) {
                return;
            }

            String[] rec = new String[13];
            int i = 0;
            rec[i++] = Timestamps.toString(command.getGenerationTime());
            rec[i++] = printAttribute(tuple, PreparedCommand.CNAME_CMDNAME);
            rec[i++] = printArguments(tuple);
            rec[i++] = printAttribute(tuple, PreparedCommand.CNAME_ORIGIN);
            rec[i++] = printAttribute(tuple, PreparedCommand.CNAME_SEQNUM);
            rec[i++] = printAttribute(tuple, PreparedCommand.CNAME_USERNAME);
            rec[i++] = printAttribute(tuple, "queue");
            rec[i++] = printAttribute(tuple, PreparedCommand.CNAME_BINARY);
            rec[i++] = printAttribute(tuple, CommandHistoryPublisher.AcknowledgeQueued_KEY + SUFFIX_STATUS);
            rec[i++] = printAttribute(tuple, CommandHistoryPublisher.AcknowledgeReleased_KEY + SUFFIX_STATUS);
            rec[i++] = printAttribute(tuple, CommandHistoryPublisher.AcknowledgeSent_KEY + SUFFIX_STATUS);
            rec[i++] = printAttribute(tuple, CommandHistoryPublisher.CommandComplete_KEY + SUFFIX_STATUS);
            rec[i++] = printAttribute(tuple, CommandHistoryPublisher.CommandComplete_KEY + SUFFIX_RETURN);

            HttpBody body = HttpBody.newBuilder()
                    .setData(toByteString(rec))
                    .build();
            observer.next(body);
        }

        private String printAttribute(Tuple tuple, String attributeName) {
            if (tuple.hasColumn(attributeName)) {
                var value = tuple.getColumn(attributeName);
                if (value instanceof byte[]) {
                    return StringConverter.arrayToHexString((byte[]) value);
                } else {
                    return "" + value;
                }
            } else {
                return "";
            }
        }

        private String printArguments(Tuple tuple) {
            if (tuple.hasColumn(PreparedCommand.CNAME_ASSIGNMENTS)) {
                AssignmentInfo assignmentProto = tuple.getColumn(PreparedCommand.CNAME_ASSIGNMENTS);
                return assignmentProto.getAssignmentList().stream()
                        .filter(assignment -> assignment.getUserInput())
                        .map(assignment -> assignment.getName() + ": "
                                + StringConverter.toString(assignment.getValue()))
                        .collect(Collectors.joining(", "));
            } else {
                return "";
            }
        }

        private ByteString toByteString(String[] rec) {
            ByteString.Output bout = ByteString.newOutput();
            CsvWriter writer = new CsvWriter(bout, columnDelimiter, StandardCharsets.UTF_8);
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
}
