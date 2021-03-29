package org.yamcs.http.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.rocksdb.RocksDBException;
import org.yamcs.parameter.ParameterCache;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterWithId;
import org.yamcs.parameter.Value;
import org.yamcs.parameter.ValueArray;
import org.yamcs.parameterarchive.ParameterArchive;
import org.yamcs.parameterarchive.ParameterRequest;
import org.yamcs.parameterarchive.ParameterValueArray;
import org.yamcs.parameterarchive.SingleParameterRetrieval;
import org.yamcs.protobuf.Pvalue.ParameterStatus;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.utils.MutableLong;
import org.yamcs.xtce.PathElement;

import com.google.common.collect.Lists;

/**
 * Retrieves values for one parameter combining parameter archive (for past data) and cache (for recent data).
 * 
 * 
 * @author nm
 *
 */
public class SingleParameterRetriever {
    final ParameterRequest spvr;
    final ParameterArchive parchive;
    final ParameterCache cache;
    final ParameterWithId pid;
    
    public SingleParameterRetriever(ParameterArchive parchive, ParameterCache cache, ParameterWithId pid, ParameterRequest spvr) {
        this.spvr = spvr;
        this.cache = cache;
        this.parchive = parchive;
        this.pid = pid;
    }
    
    public void retrieve(Consumer<ParameterValueArray> consumer) throws IOException {
        ParameterRequest spvr1 = spvr;
        if(cache!=null && !spvr.isAscending()) {//descending -> first retrieve from cache
            List<ParameterValue> pvlist = cache.getAllValues(pid.getParameter(), spvr.getStart(), spvr.getStop());
            if(pid.getPath()!=null) {
                pvlist = extractMembers(pvlist, pid.getPath());
            }
            MutableLong lastTime = new MutableLong(Long.MAX_VALUE);
            if (pvlist != null) {
                splitAndSend(pvlist, pva -> {
                    long[] timestamps = pva.getTimestamps();
                    lastTime.setLong(timestamps[timestamps.length-1]);
                    consumer.accept(pva);
                });
            }
            if(lastTime.getLong()!=Long.MAX_VALUE) {
                spvr1 = new ParameterRequest(lastTime.getLong(), spvr.getStop(), spvr.isAscending(), 
                        spvr.isRetrieveEngineeringValues(), spvr.isRetrieveRawValues(), spvr.isRetrieveParameterStatus());
            }
        }
        
        SingleParameterRetrieval spar = new SingleParameterRetrieval(parchive, pid.getQualifiedName(), spvr1);
        MutableLong lastTime = new MutableLong(Long.MAX_VALUE);
        try {
            spar.retrieve(pva -> {
                long[] timestamps = pva.getTimestamps();
                lastTime.setLong(timestamps[timestamps.length-1]);
                consumer.accept(pva);   
            });
        } catch (RocksDBException e) {
            throw new IOException(e);
        }     
        
        if(cache!=null && spvr.isAscending()) {//ascending -> send last values from cache
            long start = spvr1.getStart();
            
            if(lastTime.getLong()!=Long.MAX_VALUE) {
                start = lastTime.getLong();
            }
            
            List<ParameterValue> pvlist = cache.getAllValues(pid.getParameter(), start, spvr1.getStop());
            if (pvlist != null) {
                if(pid.getPath()!=null) {
                    pvlist = extractMembers(pvlist, pid.getPath());
                }
                pvlist = Lists.reverse(pvlist);
                splitAndSend(pvlist, consumer);
            }
        }
    }

    private List<ParameterValue> extractMembers(List<ParameterValue> pvlist, PathElement[] path) {
        List<ParameterValue> l = new ArrayList<ParameterValue>(pvlist.size());
        for(ParameterValue pv: pvlist) {
           ParameterValue pv1 = AggregateUtil.extractMember(pv, path);
           if(pv1!=null) {
               l.add(pv1);
           }
        }
        return l;
    }

    //splits the list in arrays of parameters having the same type
    private void splitAndSend(List<ParameterValue> pvlist, Consumer<ParameterValueArray> consumer) {
        int n = 0;
        int m = pvlist.size();
        ParameterValue pv0 = pvlist.get(n);
        
        for(int j=1; j<m; j++) {
            ParameterValue pv = pvlist.get(j);
            if(differentType(pv0, pv)) {
                sendToConsumer(pvlist, n, j, consumer);
                pv0 = pv;
                n = j;
            }
        }
        sendToConsumer(pvlist, n, m, consumer);
    }

    private void sendToConsumer(List<ParameterValue> pvlist, int n, int m, Consumer<ParameterValueArray> consumer) {
        ParameterValue pv0 = pvlist.get(n);
        ValueArray rawValues =  null;
        if(pv0.getRawValue()!=null) {
            rawValues = new ValueArray(pv0.getRawValue().getType(), m-n);
            for(int i=n; i<m; i++) {
                rawValues.setValue(i-n, pvlist.get(i).getRawValue());
            }
        }
        
        ValueArray engValues =  null;
        if(pv0.getEngValue()!=null) {
            engValues = new ValueArray(pv0.getEngValue().getType(), m-n);
            for(int i=n; i<m; i++) {
                engValues.setValue(i-n, pvlist.get(i).getEngValue());
            }
        }
        long[] timestamps = new long[m-n];
        ParameterStatus[] statuses = new ParameterStatus[m-n];
        for(int i=n; i<m; i++) {
            ParameterValue pv = pvlist.get(i);
            timestamps[i-n] = pv.getGenerationTime();
            statuses[i-n] = pv.getStatus().toProtoBuf();
        }
        ParameterValueArray pva = new ParameterValueArray(timestamps, engValues, rawValues, statuses);
        consumer.accept(pva);
    }

    private boolean differentType(ParameterValue pv0, ParameterValue pv1) {
        return differentType(pv0.getRawValue(), pv1.getRawValue()) || differentType(pv0.getEngValue(), pv1.getEngValue());
    }

    private boolean differentType(Value v1, Value v2) {
        if(v1==null) {
            return v2!=null;
        }
        if(v2==null) {
            return true;
        }
        
        return v1.getType()!=v2.getType();
    }
}
