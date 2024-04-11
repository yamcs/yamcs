package org.yamcs.archive;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.mdb.Mdb;
import org.yamcs.parameter.BasicParameterValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersService;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.SqlBuilder;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.protobuf.Db.ProtoDataType;

/**
 * Replays parameters from tables recorded by the {@link org.yamcs.archive.ParameterRecorder}
 * 
 * @author nm
 *
 */
public class ParameterReplayHandler implements ReplayHandler {
    Set<String> includeGroups = new HashSet<>();
    Set<String> excludeGroups = new HashSet<>();
    final Mdb mdb;
    ReplayOptions request;
    static final Logger log = LoggerFactory.getLogger(ParameterReplayHandler.class);
    boolean emptyReplay;

    public ParameterReplayHandler(Mdb mdb) {
        this.mdb = mdb;
    }

    @Override
    public void setRequest(ReplayOptions newRequest) {
        this.request = newRequest;
        includeGroups.clear();
        excludeGroups.clear();

        includeGroups.addAll(newRequest.getPpRequest().getGroupNameFilterList());
        excludeGroups.addAll(newRequest.getPpRequest().getGroupNameExcludeList());
        emptyReplay = false;
        if (!includeGroups.isEmpty() && !excludeGroups.isEmpty()) {
            includeGroups.removeAll(excludeGroups);
            if (includeGroups.isEmpty()) {
                log.info("No group remaining after removing the exclusion, this is an empty replay");
                emptyReplay = true;
            }
        }
    }

    /**
     * Provides a select statement like this:
     * 
     * <pre>
     *  select n,* from pp
     *  where group in (grp1, grp2,...)
     *  and gentime&gt;x and gentime&lt;y
     * </pre>
     * 
     * The definition of the PP table is in {@link ParameterRecorder}
     */
    @Override
    public SqlBuilder getSelectCmd() {
        if (emptyReplay) {
            return null;
        }

        SqlBuilder sqlb = ReplayHandler.init(ParameterRecorder.TABLE_NAME, ProtoDataType.PP, request);

        if (!includeGroups.isEmpty()) {
            sqlb.whereColIn("group", includeGroups);
        } else if (!excludeGroups.isEmpty()) {
            sqlb.whereColNotIn("group", excludeGroups);
        }
        return sqlb;
    }

    @Override
    public List<ParameterValue> transform(Tuple t) {
        // loop through all the columns containing values
        // the first column is the ProtoDataType.PP (from the select above),
        // then are the fixed ones from PP_TUPLE_DEFINITION
        List<ParameterValue> pvlist = new ArrayList<>();
        for (int i = StandardTupleDefinitions.PARAMETER.size() + 1; i < t.size(); i++) {
            String colName = t.getColumnDefinition(i).getName();
            Object o = t.getColumn(i);
            ParameterValue pv;
            if (o instanceof ParameterValue) {
                pv = (ParameterValue) o;
            } else if (o instanceof org.yamcs.protobuf.Pvalue.ParameterValue) {
                pv = BasicParameterValue.fromGpb(t.getColumnDefinition(i).getName(),
                        (org.yamcs.protobuf.Pvalue.ParameterValue) o);
            } else {
                log.warn("got unexpected value for column {}: {}", colName, o);
                continue;
            }
            Parameter p = mdb.getParameter(pv.getParameterQualifiedName());
            if (p == null) {
                if (Mdb.isSystemParameter(pv.getParameterQualifiedName())) {
                    p = SystemParametersService.createSystemParameter(mdb, pv.getParameterQualifiedName(),
                            pv.getEngValue());
                } else {
                    log.info("Cannot find a parameter with fqn {}", pv.getParameterQualifiedName());
                    continue;
                }
            }
            pv.setParameter(p);
            pvlist.add(pv);
        }
        return pvlist;
    }
}
