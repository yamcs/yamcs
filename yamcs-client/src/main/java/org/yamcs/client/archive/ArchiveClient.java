package org.yamcs.client.archive;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.yamcs.api.MethodHandler;
import org.yamcs.api.Observer;
import org.yamcs.client.Command;
import org.yamcs.client.Helpers;
import org.yamcs.client.Page;
import org.yamcs.client.StreamReceiver;
import org.yamcs.client.StreamSender;
import org.yamcs.client.archive.ArchiveClient.IndexOptions.FilterOption;
import org.yamcs.client.archive.ArchiveClient.IndexOptions.IndexOption;
import org.yamcs.client.archive.ArchiveClient.IndexOptions.PacketOption;
import org.yamcs.client.archive.ArchiveClient.ListOptions.AscendingOption;
import org.yamcs.client.archive.ArchiveClient.ListOptions.LimitOption;
import org.yamcs.client.archive.ArchiveClient.ListOptions.ListOption;
import org.yamcs.client.archive.ArchiveClient.ListOptions.NoRealtimeOption;
import org.yamcs.client.archive.ArchiveClient.ListOptions.NoRepeatOption;
import org.yamcs.client.archive.ArchiveClient.ListOptions.SourceOption;
import org.yamcs.client.archive.ArchiveClient.RangeOptions.MinimumGapOption;
import org.yamcs.client.archive.ArchiveClient.RangeOptions.RangeOption;
import org.yamcs.client.archive.ArchiveClient.StreamOptions.CommandOption;
import org.yamcs.client.archive.ArchiveClient.StreamOptions.EventSourceOption;
import org.yamcs.client.archive.ArchiveClient.StreamOptions.StreamOption;
import org.yamcs.client.base.AbstractPage;
import org.yamcs.client.base.ResponseObserver;
import org.yamcs.protobuf.AlarmData;
import org.yamcs.protobuf.Archive.GetParameterSamplesRequest;
import org.yamcs.protobuf.Archive.ListParameterHistoryRequest;
import org.yamcs.protobuf.Archive.ListParameterHistoryResponse;
import org.yamcs.protobuf.Archive.StreamParameterValuesRequest;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.CommandsApiClient;
import org.yamcs.protobuf.CreateTagRequest;
import org.yamcs.protobuf.DeleteTagRequest;
import org.yamcs.protobuf.EditTagRequest;
import org.yamcs.protobuf.EventsApiClient;
import org.yamcs.protobuf.GetParameterRangesRequest;
import org.yamcs.protobuf.IndexGroup;
import org.yamcs.protobuf.IndexResponse;
import org.yamcs.protobuf.IndexesApiClient;
import org.yamcs.protobuf.ListCommandHistoryIndexRequest;
import org.yamcs.protobuf.ListCommandsRequest;
import org.yamcs.protobuf.ListCommandsResponse;
import org.yamcs.protobuf.ListCompletenessIndexRequest;
import org.yamcs.protobuf.ListEventIndexRequest;
import org.yamcs.protobuf.ListEventsRequest;
import org.yamcs.protobuf.ListEventsResponse;
import org.yamcs.protobuf.ListPacketIndexRequest;
import org.yamcs.protobuf.ListParameterIndexRequest;
import org.yamcs.protobuf.ListTagsRequest;
import org.yamcs.protobuf.ListTagsResponse;
import org.yamcs.protobuf.PacketsApiClient;
import org.yamcs.protobuf.ParameterArchiveApiClient;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Pvalue.Ranges;
import org.yamcs.protobuf.Pvalue.Ranges.Range;
import org.yamcs.protobuf.Pvalue.TimeSeries;
import org.yamcs.protobuf.Pvalue.TimeSeries.Sample;
import org.yamcs.protobuf.StreamArchiveApiClient;
import org.yamcs.protobuf.StreamCommandIndexRequest;
import org.yamcs.protobuf.StreamCommandsRequest;
import org.yamcs.protobuf.StreamCompletenessIndexRequest;
import org.yamcs.protobuf.StreamEventIndexRequest;
import org.yamcs.protobuf.StreamEventsRequest;
import org.yamcs.protobuf.StreamIndexRequest;
import org.yamcs.protobuf.StreamPacketIndexRequest;
import org.yamcs.protobuf.StreamPacketsRequest;
import org.yamcs.protobuf.StreamParameterIndexRequest;
import org.yamcs.protobuf.Table.GetTableDataRequest;
import org.yamcs.protobuf.Table.ReadRowsRequest;
import org.yamcs.protobuf.Table.Row;
import org.yamcs.protobuf.Table.TableData;
import org.yamcs.protobuf.Table.TableData.TableRecord;
import org.yamcs.protobuf.Table.WriteRowsRequest;
import org.yamcs.protobuf.Table.WriteRowsResponse;
import org.yamcs.protobuf.TableApiClient;
import org.yamcs.protobuf.TagApiClient;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.IndexResult;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.protobuf.alarms.AlarmsApiClient;
import org.yamcs.protobuf.alarms.ListAlarmsRequest;
import org.yamcs.protobuf.alarms.ListAlarmsResponse;

import com.google.protobuf.Timestamp;

public class ArchiveClient {

    private String instance;
    private IndexesApiClient indexService;
    private CommandsApiClient commandService;
    private ParameterArchiveApiClient parameterArchiveService;
    private StreamArchiveApiClient streamArchiveService;
    private AlarmsApiClient alarmService;
    private TableApiClient tableService;
    private EventsApiClient eventService;
    private TagApiClient tagService;
    private PacketsApiClient packetService;

    public ArchiveClient(MethodHandler handler, String instance) {
        this.instance = instance;
        indexService = new IndexesApiClient(handler);
        commandService = new CommandsApiClient(handler);
        parameterArchiveService = new ParameterArchiveApiClient(handler);
        streamArchiveService = new StreamArchiveApiClient(handler);
        alarmService = new AlarmsApiClient(handler);
        tableService = new TableApiClient(handler);
        eventService = new EventsApiClient(handler);
        tagService = new TagApiClient(handler);
        packetService = new PacketsApiClient(handler);
    }

    public String getInstance() {
        return instance;
    }

    public CompletableFuture<Page<IndexGroup>> listCommandIndex(Instant start, Instant stop, ListOption... options) {
        ListCommandHistoryIndexRequest.Builder requestb = ListCommandHistoryIndexRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        for (ListOption option : options) {
            if (option instanceof LimitOption) {
                requestb.setLimit(((LimitOption) option).limit);
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        return new CommandIndexPage(requestb.build()).future();
    }

    public CompletableFuture<Page<IndexGroup>> listPacketIndex(Instant start, Instant stop, ListOption... options) {
        ListPacketIndexRequest.Builder requestb = ListPacketIndexRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        for (ListOption option : options) {
            if (option instanceof LimitOption) {
                requestb.setLimit(((LimitOption) option).limit);
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        return new PacketIndexPage(requestb.build()).future();
    }

    public CompletableFuture<Page<IndexGroup>> listParameterIndex(Instant start, Instant stop, ListOption... options) {
        ListParameterIndexRequest.Builder requestb = ListParameterIndexRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        for (ListOption option : options) {
            if (option instanceof LimitOption) {
                requestb.setLimit(((LimitOption) option).limit);
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        return new ParameterIndexPage(requestb.build()).future();
    }

    public CompletableFuture<Page<IndexGroup>> listEventIndex(Instant start, Instant stop, ListOption... options) {
        ListEventIndexRequest.Builder requestb = ListEventIndexRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        for (ListOption option : options) {
            if (option instanceof LimitOption) {
                requestb.setLimit(((LimitOption) option).limit);
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        return new EventIndexPage(requestb.build()).future();
    }

    public CompletableFuture<Page<IndexGroup>> listCompletenessIndex(Instant start, Instant stop,
            ListOption... options) {
        ListCompletenessIndexRequest.Builder requestb = ListCompletenessIndexRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        for (ListOption option : options) {
            if (option instanceof LimitOption) {
                requestb.setLimit(((LimitOption) option).limit);
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        return new CompletenessIndexPage(requestb.build()).future();
    }

    public CompletableFuture<Void> streamPacketIndex(StreamReceiver<ArchiveRecord> consumer, Instant start,
            Instant stop) {
        StreamPacketIndexRequest.Builder requestb = StreamPacketIndexRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        CompletableFuture<Void> f = new CompletableFuture<>();
        indexService.streamPacketIndex(null, requestb.build(), new Observer<ArchiveRecord>() {

            @Override
            public void next(ArchiveRecord message) {
                consumer.accept(message);
            }

            @Override
            public void completeExceptionally(Throwable t) {
                f.completeExceptionally(t);
            }

            @Override
            public void complete() {
                f.complete(null);
            }
        });
        return f;
    }

    public CompletableFuture<Void> streamParameterIndex(StreamReceiver<ArchiveRecord> consumer, Instant start,
            Instant stop) {
        StreamParameterIndexRequest.Builder requestb = StreamParameterIndexRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        CompletableFuture<Void> f = new CompletableFuture<>();
        indexService.streamParameterIndex(null, requestb.build(), new Observer<ArchiveRecord>() {

            @Override
            public void next(ArchiveRecord message) {
                consumer.accept(message);
            }

            @Override
            public void completeExceptionally(Throwable t) {
                f.completeExceptionally(t);
            }

            @Override
            public void complete() {
                f.complete(null);
            }
        });
        return f;
    }

    public CompletableFuture<Void> streamCommandIndex(StreamReceiver<ArchiveRecord> consumer, Instant start,
            Instant stop) {
        StreamCommandIndexRequest.Builder requestb = StreamCommandIndexRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        CompletableFuture<Void> f = new CompletableFuture<>();
        indexService.streamCommandIndex(null, requestb.build(), new Observer<ArchiveRecord>() {

            @Override
            public void next(ArchiveRecord message) {
                consumer.accept(message);
            }

            @Override
            public void completeExceptionally(Throwable t) {
                f.completeExceptionally(t);
            }

            @Override
            public void complete() {
                f.complete(null);
            }
        });
        return f;
    }

    public CompletableFuture<Void> streamEventIndex(StreamReceiver<ArchiveRecord> consumer, Instant start,
            Instant stop) {
        StreamEventIndexRequest.Builder requestb = StreamEventIndexRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        CompletableFuture<Void> f = new CompletableFuture<>();
        indexService.streamEventIndex(null, requestb.build(), new Observer<ArchiveRecord>() {

            @Override
            public void next(ArchiveRecord message) {
                consumer.accept(message);
            }

            @Override
            public void completeExceptionally(Throwable t) {
                f.completeExceptionally(t);
            }

            @Override
            public void complete() {
                f.complete(null);
            }
        });
        return f;
    }

    public CompletableFuture<Void> streamCompletenessIndex(StreamReceiver<ArchiveRecord> consumer, Instant start,
            Instant stop) {
        StreamCompletenessIndexRequest.Builder requestb = StreamCompletenessIndexRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        CompletableFuture<Void> f = new CompletableFuture<>();
        indexService.streamCompletenessIndex(null, requestb.build(), new Observer<ArchiveRecord>() {

            @Override
            public void next(ArchiveRecord message) {
                consumer.accept(message);
            }

            @Override
            public void completeExceptionally(Throwable t) {
                f.completeExceptionally(t);
            }

            @Override
            public void complete() {
                f.complete(null);
            }
        });
        return f;
    }

    public CompletableFuture<Void> streamIndex(StreamReceiver<IndexResult> consumer, Instant start, Instant stop,
            IndexOption... options) {
        StreamIndexRequest.Builder requestb = StreamIndexRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        for (IndexOption option : options) {
            if (option instanceof FilterOption) {
                for (String filter : ((FilterOption) option).filter) {
                    requestb.addFilters(filter);
                }
            } else if (option instanceof PacketOption) {
                for (String packet : ((PacketOption) option).packets) {
                    requestb.addPacketnames(packet);
                }
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        CompletableFuture<Void> f = new CompletableFuture<>();
        indexService.streamIndex(null, requestb.build(), new Observer<IndexResult>() {

            @Override
            public void next(IndexResult message) {
                consumer.accept(message);
            }

            @Override
            public void completeExceptionally(Throwable t) {
                f.completeExceptionally(t);
            }

            @Override
            public void complete() {
                f.complete(null);
            }
        });
        return f;
    }

    public CompletableFuture<Page<Command>> listCommands() {
        return listCommands(null, null);
    }

    public CompletableFuture<Page<Command>> listCommands(Instant start, Instant stop) {
        ListCommandsRequest.Builder requestb = ListCommandsRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        return new CommandPage(requestb.build()).future();
    }

    public CompletableFuture<Void> streamCommands(StreamReceiver<Command> consumer, Instant start,
            Instant stop, StreamOption... options) {
        StreamCommandsRequest.Builder requestb = StreamCommandsRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        for (StreamOption option : options) {
            if (option instanceof CommandOption) {
                for (String command : ((CommandOption) option).commands) {
                    requestb.addName(command);
                }
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        CompletableFuture<Void> f = new CompletableFuture<>();
        commandService.streamCommands(null, requestb.build(), new Observer<CommandHistoryEntry>() {

            @Override
            public void next(CommandHistoryEntry message) {
                Command command = new Command(message.getId(), message.getCommandName(), message.getOrigin(),
                        message.getSequenceNumber(), Helpers.toInstant(message.getGenerationTime()));
                command.merge(message);
                consumer.accept(command);
            }

            @Override
            public void completeExceptionally(Throwable t) {
                f.completeExceptionally(t);
            }

            @Override
            public void complete() {
                f.complete(null);
            }
        });
        return f;
    }

    public CompletableFuture<Page<Event>> listEvents() {
        return listEvents(null, null);
    }

    public CompletableFuture<Page<Event>> listEvents(Instant start, Instant stop) {
        ListEventsRequest.Builder requestb = ListEventsRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        return new EventPage(requestb.build()).future();
    }

    public CompletableFuture<Void> streamEvents(StreamReceiver<Event> consumer, Instant start, Instant stop,
            StreamOption... options) {
        StreamEventsRequest.Builder requestb = StreamEventsRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        for (StreamOption option : options) {
            if (option instanceof EventSourceOption) {
                for (String source : ((EventSourceOption) option).eventSources) {
                    requestb.addSource(source);
                }
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        CompletableFuture<Void> f = new CompletableFuture<>();
        eventService.streamEvents(null, requestb.build(), new Observer<Event>() {

            @Override
            public void next(Event message) {
                consumer.accept(message);
            }

            @Override
            public void completeExceptionally(Throwable t) {
                f.completeExceptionally(t);
            }

            @Override
            public void complete() {
                f.complete(null);
            }
        });
        return f;
    }

    public CompletableFuture<Void> streamPackets(StreamReceiver<TmPacketData> consumer, Instant start, Instant stop,
            StreamOption... options) {
        StreamPacketsRequest.Builder requestb = StreamPacketsRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        for (StreamOption option : options) {
            if (option instanceof StreamOptions.PacketOption) {
                for (String packet : ((StreamOptions.PacketOption) option).packets) {
                    requestb.addName(packet);
                }
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        CompletableFuture<Void> f = new CompletableFuture<>();
        packetService.streamPackets(null, requestb.build(), new Observer<TmPacketData>() {

            @Override
            public void next(TmPacketData message) {
                consumer.accept(message);
            }

            @Override
            public void completeExceptionally(Throwable t) {
                f.completeExceptionally(t);
            }

            @Override
            public void complete() {
                f.complete(null);
            }
        });
        return f;
    }

    public CompletableFuture<Void> streamValues(List<String> parameters,
            StreamReceiver<Map<String, ParameterValue>> consumer, Instant start, Instant stop) {
        StreamParameterValuesRequest.Builder requestb = StreamParameterValuesRequest.newBuilder()
                .setInstance(instance);
        for (String parameter : parameters) {
            requestb.addIds(Helpers.toNamedObjectId(parameter));
        }
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        CompletableFuture<Void> f = new CompletableFuture<>();
        streamArchiveService.streamParameterValues(null, requestb.build(), new Observer<ParameterData>() {

            @Override
            public void next(ParameterData message) {
                Map<String, ParameterValue> map = new LinkedHashMap<>();
                for (ParameterValue pvalue : message.getParameterList()) {
                    map.put(Helpers.toName(pvalue.getId()), pvalue);
                }
                consumer.accept(map);
            }

            @Override
            public void completeExceptionally(Throwable t) {
                f.completeExceptionally(t);
            }

            @Override
            public void complete() {
                f.complete(null);
            }
        });
        return f;
    }

    public CompletableFuture<ArchiveTag> createTag(CreateTagRequest request) {
        CreateTagRequest.Builder requestb = request.toBuilder()
                .setInstance(instance);
        CompletableFuture<ArchiveTag> f = new CompletableFuture<>();
        tagService.createTag(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<List<ArchiveTag>> listTags(Instant start, Instant stop) {
        ListTagsRequest.Builder requestb = ListTagsRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        CompletableFuture<ListTagsResponse> f = new CompletableFuture<>();
        tagService.listTags(null, requestb.build(), new ResponseObserver<>(f));
        return f.thenApply(response -> response.getTagList());
    }

    public CompletableFuture<ArchiveTag> updateTag(EditTagRequest request) {
        EditTagRequest.Builder requestb = request.toBuilder()
                .setInstance(instance);
        CompletableFuture<ArchiveTag> f = new CompletableFuture<>();
        tagService.updateTag(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<ArchiveTag> deleteTag(long tagTime, int tagId) {
        DeleteTagRequest.Builder requestb = DeleteTagRequest.newBuilder()
                .setInstance(instance)
                .setTagTime(tagTime)
                .setTagId(tagId);
        CompletableFuture<ArchiveTag> f = new CompletableFuture<>();
        tagService.deleteTag(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<List<AlarmData>> listAlarms() {
        // TODO add pagination on server
        return listAlarms(null, null);
    }

    public CompletableFuture<List<AlarmData>> listAlarms(Instant start, Instant stop) {
        // TODO add pagination on server
        ListAlarmsRequest.Builder requestb = ListAlarmsRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        CompletableFuture<ListAlarmsResponse> f = new CompletableFuture<>();
        alarmService.listAlarms(null, requestb.build(), new ResponseObserver<>(f));
        return f.thenApply(response -> response.getAlarmsList());
    }

    public CompletableFuture<List<TableRecord>> listRecords(String table) {
        // TODO add pagination on server
        GetTableDataRequest.Builder requestb = GetTableDataRequest.newBuilder()
                .setInstance(instance)
                .setName(table);
        CompletableFuture<TableData> f = new CompletableFuture<>();
        tableService.getTableData(null, requestb.build(), new ResponseObserver<>(f));
        return f.thenApply(response -> response.getRecordList());
    }

    public CompletableFuture<Page<ParameterValue>> listValues(String parameter, ListOption... options) {
        return listValues(parameter, null, null, options);
    }

    public CompletableFuture<Page<ParameterValue>> listValues(String parameter, Instant start, Instant stop,
            ListOption... options) {
        ListParameterHistoryRequest.Builder requestb = ListParameterHistoryRequest.newBuilder()
                .setInstance(instance)
                .setName(parameter);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        for (ListOption option : options) {
            if (option instanceof AscendingOption) {
                requestb.setOrder(((AscendingOption) option).ascending ? "asc" : "desc");
            } else if (option instanceof NoRepeatOption) {
                requestb.setNorepeat(((NoRepeatOption) option).noRepeat);
            } else if (option instanceof NoRealtimeOption) {
                requestb.setNorealtime(((NoRealtimeOption) option).noRealtime);
            } else if (option instanceof LimitOption) {
                requestb.setLimit(((LimitOption) option).limit);
            } else if (option instanceof SourceOption) {
                requestb.setSource(((SourceOption) option).source);
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        return new ValuePage(requestb.build()).future();
    }

    public CompletableFuture<List<Sample>> getSamples(String parameter, Instant start, Instant stop) {
        GetParameterSamplesRequest.Builder requestb = GetParameterSamplesRequest.newBuilder()
                .setInstance(instance)
                .setName(parameter)
                .setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()))
                .setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        CompletableFuture<TimeSeries> f = new CompletableFuture<>();
        parameterArchiveService.getParameterSamples(null, requestb.build(), new ResponseObserver<>(f));
        return f.thenApply(response -> response.getSampleList());
    }

    public CompletableFuture<List<Range>> getRanges(String parameter, Instant start, Instant stop,
            RangeOption... options) {
        GetParameterRangesRequest.Builder requestb = GetParameterRangesRequest.newBuilder()
                .setInstance(instance)
                .setName(parameter)
                .setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()))
                .setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        for (RangeOption option : options) {
            if (option instanceof MinimumGapOption) {
                requestb.setMinGap(((MinimumGapOption) option).millis);
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        CompletableFuture<Ranges> f = new CompletableFuture<>();
        parameterArchiveService.getParameterRanges(null, requestb.build(), new ResponseObserver<>(f));
        return f.thenApply(ranges -> ranges.getRangeList());
    }

    public CompletableFuture<Void> dumpTable(String table, StreamReceiver<Row> consumer) {
        ReadRowsRequest.Builder requestb = ReadRowsRequest.newBuilder()
                .setInstance(instance)
                .setTable(table);
        CompletableFuture<Void> f = new CompletableFuture<>();
        tableService.readRows(null, requestb.build(), new Observer<Row>() {

            @Override
            public void next(Row message) {
                consumer.accept(message);
            }

            @Override
            public void completeExceptionally(Throwable t) {
                f.completeExceptionally(t);
            }

            @Override
            public void complete() {
                f.complete(null);
            }
        });
        return f;
    }

    public TableLoader createTableLoader(String table) {
        WriteRowsRequest.Builder requestb = WriteRowsRequest.newBuilder()
                .setInstance(instance)
                .setTable(table);
        CompletableFuture<WriteRowsResponse> f = new CompletableFuture<>();
        Observer<WriteRowsRequest> clientObserver = tableService.writeRows(null, new ResponseObserver<>(f));
        clientObserver.next(requestb.build());
        return new TableLoader(clientObserver, f);
    }

    private class CommandIndexPage extends AbstractPage<ListCommandHistoryIndexRequest, IndexResponse, IndexGroup> {

        public CommandIndexPage(ListCommandHistoryIndexRequest request) {
            super(request, "group");
        }

        @Override
        protected void fetch(ListCommandHistoryIndexRequest request, Observer<IndexResponse> observer) {
            indexService.listCommandHistoryIndex(null, request, observer);
        }
    }

    private class CommandPage extends AbstractPage<ListCommandsRequest, ListCommandsResponse, Command> {

        public CommandPage(ListCommandsRequest request) {
            super(request, "entry");
        }

        @Override
        protected void fetch(ListCommandsRequest request, Observer<ListCommandsResponse> observer) {
            commandService.listCommands(null, request, observer);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected List<Command> mapRepeatableField(Object field) {
            return ((List<CommandHistoryEntry>) field).stream().map(entry -> {
                Command command = new Command(entry.getId(), entry.getCommandName(), entry.getOrigin(),
                        entry.getSequenceNumber(), Helpers.toInstant(entry.getGenerationTime()));
                command.merge(entry);
                return command;
            }).collect(Collectors.toList());
        }
    }

    private class EventPage extends AbstractPage<ListEventsRequest, ListEventsResponse, Event> {

        public EventPage(ListEventsRequest request) {
            super(request, "event");
        }

        @Override
        protected void fetch(ListEventsRequest request, Observer<ListEventsResponse> observer) {
            eventService.listEvents(null, request, observer);
        }
    }

    private class PacketIndexPage extends AbstractPage<ListPacketIndexRequest, IndexResponse, IndexGroup> {

        public PacketIndexPage(ListPacketIndexRequest request) {
            super(request, "group");
        }

        @Override
        protected void fetch(ListPacketIndexRequest request, Observer<IndexResponse> observer) {
            indexService.listPacketIndex(null, request, observer);
        }
    }

    private class ParameterIndexPage extends AbstractPage<ListParameterIndexRequest, IndexResponse, IndexGroup> {

        public ParameterIndexPage(ListParameterIndexRequest request) {
            super(request, "group");
        }

        @Override
        protected void fetch(ListParameterIndexRequest request, Observer<IndexResponse> observer) {
            indexService.listParameterIndex(null, request, observer);
        }
    }

    private class EventIndexPage extends AbstractPage<ListEventIndexRequest, IndexResponse, IndexGroup> {

        public EventIndexPage(ListEventIndexRequest request) {
            super(request, "group");
        }

        @Override
        protected void fetch(ListEventIndexRequest request, Observer<IndexResponse> observer) {
            indexService.listEventIndex(null, request, observer);
        }
    }

    private class CompletenessIndexPage extends AbstractPage<ListCompletenessIndexRequest, IndexResponse, IndexGroup> {

        public CompletenessIndexPage(ListCompletenessIndexRequest request) {
            super(request, "group");
        }

        @Override
        protected void fetch(ListCompletenessIndexRequest request, Observer<IndexResponse> observer) {
            indexService.listCompletenessIndex(null, request, observer);
        }
    }

    private class ValuePage
            extends AbstractPage<ListParameterHistoryRequest, ListParameterHistoryResponse, ParameterValue> {

        public ValuePage(ListParameterHistoryRequest request) {
            super(request, "parameter");
        }

        @Override
        protected void fetch(ListParameterHistoryRequest request, Observer<ListParameterHistoryResponse> observer) {
            parameterArchiveService.listParameterHistory(null, request, observer);
        }
    }

    public static class TableLoader implements StreamSender<Row, WriteRowsResponse> {

        private Observer<WriteRowsRequest> clientObserver;
        private CompletableFuture<WriteRowsResponse> responseFuture;

        private TableLoader(Observer<WriteRowsRequest> clientObserver,
                CompletableFuture<WriteRowsResponse> responseFuture) {
            this.clientObserver = clientObserver;
            this.responseFuture = responseFuture;
        }

        @Override
        public void send(Row message) {
            clientObserver.next(WriteRowsRequest.newBuilder().setRow(message).build());
        }

        @Override
        public CompletableFuture<WriteRowsResponse> complete() {
            clientObserver.complete();
            return responseFuture;
        }
    }

    public static final class ListOptions {

        public static interface ListOption {
        }

        public static ListOption ascending(boolean ascending) {
            return new AscendingOption(ascending);
        }

        public static ListOption limit(int limit) {
            return new LimitOption(limit);
        }

        public static ListOption noRepeat(boolean noRepeat) {
            return new NoRepeatOption(noRepeat);
        }

        public static ListOption noRealtime(boolean noRealtime) {
            return new NoRealtimeOption(noRealtime);
        }

        public static ListOption source(String source) {
            return new SourceOption(source);
        }

        static final class AscendingOption implements ListOption {
            final boolean ascending;

            public AscendingOption(boolean ascending) {
                this.ascending = ascending;
            }
        }

        static final class LimitOption implements ListOption {
            final int limit;

            public LimitOption(int limit) {
                this.limit = limit;
            }
        }

        static final class NoRepeatOption implements ListOption {
            final boolean noRepeat;

            public NoRepeatOption(boolean noRepeat) {
                this.noRepeat = noRepeat;
            }
        }

        static final class NoRealtimeOption implements ListOption {
            final boolean noRealtime;

            public NoRealtimeOption(boolean noRealtime) {
                this.noRealtime = noRealtime;
            }
        }

        static final class SourceOption implements ListOption {
            final String source;

            public SourceOption(String source) {
                this.source = source;
            }
        }
    }

    public static final class RangeOptions {

        public static interface RangeOption {
        }

        public static RangeOption minimumGap(long millis) {
            return new MinimumGapOption(millis);
        }

        static final class MinimumGapOption implements RangeOption {
            final long millis;

            public MinimumGapOption(long millis) {
                this.millis = millis;
            }
        }
    }

    public static final class IndexOptions {

        public static interface IndexOption {
        }

        public static IndexOption filter(String... filter) {
            return new FilterOption(filter);
        }

        public static IndexOption packets(String... packets) {
            return new PacketOption(packets);
        }

        static final class FilterOption implements IndexOption {
            final String[] filter;

            public FilterOption(String... filter) {
                this.filter = filter;
            }
        }

        static final class PacketOption implements IndexOption {
            final String[] packets;

            public PacketOption(String... packets) {
                this.packets = packets;
            }
        }
    }

    public static final class StreamOptions {

        public static interface StreamOption {
        }

        public static StreamOption commands(String... commands) {
            return new CommandOption(commands);
        }

        public static StreamOption eventSources(String... eventSources) {
            return new EventSourceOption(eventSources);
        }

        public static StreamOption packets(String... packets) {
            return new PacketOption(packets);
        }

        static final class CommandOption implements StreamOption {
            final String[] commands;

            public CommandOption(String... commands) {
                this.commands = commands;
            }
        }

        static final class EventSourceOption implements StreamOption {
            final String[] eventSources;

            public EventSourceOption(String... eventSources) {
                this.eventSources = eventSources;
            }
        }

        static final class PacketOption implements StreamOption {
            final String[] packets;

            public PacketOption(String... packets) {
                this.packets = packets;
            }
        }
    }
}
