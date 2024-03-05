package org.yamcs.timeline;

import java.util.List;
import java.util.UUID;

import org.yamcs.StandardTupleDefinitions;
import org.yamcs.archive.CommandHistoryRecorder;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.http.BadRequestException;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.ItemFilter;
import org.yamcs.protobuf.ItemFilter.FilterCriterion;
import org.yamcs.protobuf.TimelineSourceCapabilities;
import org.yamcs.utils.TimeInterval;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.SqlBuilder;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.ResultListener;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlStatement;

/**
 * Implements the "commands" timeline source providing items derived from the command history.
 * <p>
 * The filtering criteria which can be applied is based on the command name patterns (regular expressions matching
 * command names)
 *
 */
public class CommandItemProvider implements ItemProvider {
    public final static String CRIT_KEY_CMD_NAME_PATTERN = "cmdNamePattern";
    private Log log;
    private YarchDatabaseInstance ydb;
    TupleMatcher matcher;

    public CommandItemProvider(String yamcsInstance) {
        log = new Log(getClass(), yamcsInstance);
        ydb = YarchDatabase.getInstance(yamcsInstance);
        matcher = new TupleMatcher();
    }

    @Override
    public TimelineItem getItem(String id) {
        return null;
    }

    @Override
    public void getItems(int limit, String next, RetrievalFilter filter, ItemReceiver consumer) {

        SqlBuilder sqlb = new SqlBuilder(CommandHistoryRecorder.TABLE_NAME);
        TimeInterval interval = filter.getTimeInterval();
        if (interval.hasEnd()) {
            sqlb.where("gentime < ?", interval.getEnd());
        }
        if (interval.hasStart()) {
            sqlb.where("gentime >= ?", interval.getStart());
        }

        sqlb.limit(limit + 1);

        try {
            StreamSqlStatement stmt = ydb.createStatement(sqlb.toString(),
                    sqlb.getQueryArguments().toArray());
            ydb.execute(stmt, new ResultListener() {

                @Override
                public void next(Tuple tuple) {
                    if (matcher.matches(filter, tuple)) {
                        consumer.next(toItem(tuple));
                    }
                }

                @Override
                public void completeExceptionally(Throwable t) {
                    consumer.completeExceptionally(t);
                }

                @Override
                public void complete() {
                    consumer.complete(null);
                }
            });
        } catch (StreamSqlException | ParseException e) {
            log.error("Exception when executing query", e);
        }
    }

    @Override
    public void validateFilters(List<ItemFilter> filters) throws BadRequestException {
        for (var filter : filters) {
            for (var c : filter.getCriteriaList()) {
                if (!CRIT_KEY_CMD_NAME_PATTERN.equals(c.getKey())) {
                    throw new BadRequestException(
                            "Unknonw criteria key " + c.getKey() + ". Supported key: " + CRIT_KEY_CMD_NAME_PATTERN);
                }
            }
        }
    }

    @Override
    public TimelineItem addItem(TimelineItem item) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TimelineItem updateItem(TimelineItem item) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TimelineItem deleteItem(UUID uuid) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TimelineItem deleteTimelineGroup(UUID uuid) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TimelineSourceCapabilities getCapabilities() {
        return TimelineSourceCapabilities.newBuilder()
                .setReadOnly(true)
                .build();
    }

    private static TimelineEvent toItem(Tuple tuple) {
        long gentime = (Long) tuple.getColumn(PreparedCommand.CNAME_GENTIME);
        String origin = (String) tuple.getColumn(PreparedCommand.CNAME_ORIGIN);
        int sequenceNumber = (Integer) tuple.getColumn(PreparedCommand.CNAME_SEQNUM);
        String id = gentime + "-" + origin + "-" + sequenceNumber;

        TimelineEvent event = new TimelineEvent(id);
        event.setStart(gentime);
        event.setName(tuple.getColumn(PreparedCommand.CNAME_CMDNAME));
        return event;
    }

    private static class TupleMatcher extends FilterMatcher<Tuple> {
        @Override
        protected boolean criterionMatch(FilterCriterion c, Tuple tuple) {
            String cmdName = tuple.getColumn(StandardTupleDefinitions.CMDHIST_TUPLE_COL_CMDNAME);
            if (cmdName == null) {
                return false;
            }
            if (CRIT_KEY_CMD_NAME_PATTERN.equals(c.getKey())) {
                return cmdName.matches(c.getValue());
            } else {
                return false;
            }
        }
    }
}
