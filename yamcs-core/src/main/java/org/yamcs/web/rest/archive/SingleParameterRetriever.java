package org.yamcs.web.rest.archive;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import org.rocksdb.RocksDBException;
import org.yamcs.parameter.ParameterCache;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.parameter.ValueArray;
import org.yamcs.parameterarchive.ParameterArchiveV2;
import org.yamcs.parameterarchive.ParameterRequest;
import org.yamcs.parameterarchive.ParameterValueArray;
import org.yamcs.parameterarchive.SingleParameterArchiveRetrieval;
import org.yamcs.protobuf.Pvalue.ParameterStatus;
import org.yamcs.utils.MutableLong;
import org.yamcs.xtce.Parameter;

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
    final ParameterArchiveV2 parchive;
    final ParameterCache cache;
    final Parameter p;
    
    public SingleParameterRetriever(ParameterArchiveV2 parchive, ParameterCache cache, Parameter p, ParameterRequest spvr) {
        this.spvr = spvr;
        this.cache = cache;
        this.parchive = parchive;
        this.p = p;
    }
    
    public void retrieve(Consumer<ParameterValueArray> consumer) throws IOException {
        ParameterRequest spvr1 = spvr;
        if(cache!=null && !spvr.isAscending()) {//descending -> first retrieve from cache
            List<ParameterValue> pvlist = cache.getAllValues(p, spvr.getStart(), spvr.getStop());
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
        
        SingleParameterArchiveRetrieval spar = new SingleParameterArchiveRetrieval(parchive, p.getQualifiedName(), spvr1);
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
            
            List<ParameterValue> pvlist = cache.getAllValues(p, start, spvr1.getStop());
            if (pvlist != null) {
                pvlist = Lists.reverse(pvlist);
                splitAndSend(pvlist, consumer);
            }
        }
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
