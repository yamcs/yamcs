package org.yamcs.archive;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.tctm.PpProviderAdapter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.yarch.Tuple;

import com.google.protobuf.MessageLite;

public class PpReplayHandler implements ReplayHandler {
    Set<String>currentGroups=new HashSet<String>();
    final XtceDb ppdb;
    ReplayRequest request;
    final static Logger log=LoggerFactory.getLogger(PpReplayHandler.class);
    
    public PpReplayHandler(XtceDb ppdb) {
        this.ppdb=ppdb;
    }

    @Override
    public void setRequest(ReplayRequest newRequest) {
        this.request = newRequest;
        currentGroups.clear();
        currentGroups.addAll(newRequest.getPpRequest().getGroupNameFilterList());
    }

    @Override
    /**
     * provides a select statement like this:
     * select n,* from pp where ppgroup in (grp1, grp2,...) and gentime>x and gentime<y
     * The definition of the PP table is in {@link PpRecorder} 
     */
    public String getSelectCmd() {
        if(currentGroups.isEmpty())return null;
        StringBuilder sb=new StringBuilder();
        boolean first=true;
        sb.append("SELECT ").append(ProtoDataType.PP.getNumber()).
        append(",* from pp where ppgroup in(");
        for(String g:currentGroups) {
            if(first) first=false;
            else sb.append(", ");
            sb.append("'").append(g).append("'");
        }
        sb.append(")");
        XtceTmReplayHandler.appendTimeClause(sb, request, false);
        if(request.hasReverse() && request.getReverse()) {
            sb.append(" ORDER DESC");
        }
        return sb.toString();
    }

    @Override
    public MessageLite transform(Tuple t) {
        ParameterData.Builder pdb=ParameterData.newBuilder();
        //loop through all the columns containing values
        // the first column is the ProtoDataType.PP (from the select above), then are the fixed ones from PP_TUPLE_DEFINITION
        for(int i=PpProviderAdapter.PP_TUPLE_DEFINITION.size()+1; i<t.size(); i++) {
            String colName=t.getColumnDefinition(i).getName();
            Object o=t.getColumn(i);
            if(o instanceof ParameterValue) {
                //add an id
                ParameterValue.Builder pvb=ParameterValue.newBuilder((ParameterValue)o);
                pvb.setId(NamedObjectId.newBuilder().setName(colName));
                ParameterValue pv=pvb.build();
                pdb.addParameter(pv);
            } else {
                log.warn("got unexpected value for column {}: {}",colName, o);
            }
        }
        return pdb.build();
    }

    @Override
    public void reset() {
    }
}
