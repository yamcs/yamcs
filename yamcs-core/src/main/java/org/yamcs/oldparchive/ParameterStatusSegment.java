package org.yamcs.oldparchive;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Mdb.AlarmLevelType;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.ParameterStatus;
import org.yamcs.protobuf.Pvalue.RangeCondition;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.DoubleRange;

import com.google.protobuf.InvalidProtocolBufferException;

public class ParameterStatusSegment extends ObjectSegment<ParameterStatus> {
    static ParameterStatusSerializer serializer = new ParameterStatusSerializer();
    static AcquiredCache cache = new AcquiredCache();
  
    
    public ParameterStatusSegment( boolean buildForSerialisation) {
        super(serializer, buildForSerialisation);
    }


    static public final ParameterStatus getStatus(ParameterValue pv) {
        AcquisitionStatus acq = pv.getAcquisitionStatus();
        MonitoringResult mr = pv.getMonitoringResult();
        
        if(acq==AcquisitionStatus.ACQUIRED && mr == null) {
            return cache.get(pv.getExpireMills());
        }
        
        ParameterStatus.Builder pvfb =  ParameterStatus.newBuilder();
        
        if(acq!=null) {
            pvfb.setAcquisitionStatus(acq);
        }
        
        if(mr!=null) {
            pvfb.setMonitoringResult(mr);
        }
        RangeCondition rc = pv.getRangeCondition();
        if(rc!=null) {
            pvfb.setRangeCondition(rc);
        }
        
        addAlarmRange(pvfb, AlarmLevelType.WATCH, pv.getWatchRange());
        addAlarmRange(pvfb, AlarmLevelType.WARNING, pv.getWarningRange());
        addAlarmRange(pvfb, AlarmLevelType.DISTRESS, pv.getDistressRange());
        addAlarmRange(pvfb, AlarmLevelType.CRITICAL, pv.getCriticalRange());
        addAlarmRange(pvfb, AlarmLevelType.SEVERE, pv.getSevereRange());

        return pvfb.build();

    }


    private static void addAlarmRange(ParameterStatus.Builder pvfb, AlarmLevelType level, DoubleRange range) {
        if(range==null) {
            return;
        }
        pvfb.addAlarmRange(ParameterValue.toGpbAlarmRange(level, range));
    }

    

    public void addParameterValue(int pos, ParameterValue pv) {
        add(pos, getStatus(pv));
    }
    public void addParameterValue(ParameterValue pv) {
       add(getStatus(pv)); 
    }
    
    ParameterStatusSegment consolidate() {
        return (ParameterStatusSegment) super.consolidate();
    }
    public static ParameterStatusSegment parseFrom(ByteBuffer bb) throws DecodingException {
        ParameterStatusSegment r = new ParameterStatusSegment(false);
        r.parse(bb);
        return r;
    }
    
    static class ParameterStatusSerializer implements ObjectSerializer<ParameterStatus>  {
        @Override
        public byte getFormatId() {
            return BaseSegment.FORMAT_ID_ParameterStatusSegment;
        }

        @Override
        public ParameterStatus deserialize(byte[] b) throws DecodingException {
            try {
                return ParameterStatus.parseFrom(b);
            } catch (InvalidProtocolBufferException e) {
                throw new DecodingException("Cannto deserialzie ParameterStatus", e);
            }
        }

        @Override
        public byte[] serialize(ParameterStatus e) {
            return e.toByteArray();
        }
    }
    
    /**
     * cache to avoid creating unnecessary ParameterStatus objects for parameters that have no status other than acquired and expiration time
     * (likely 95% of all parameter values).
     *
     */
    static class AcquiredCache {
        static long EVICTION_INTERVAL = 3600000L;
        static long CACHE_TIME = 3600000L;
        
        static final ParameterStatus ACQUIRED_NO_EXP = ParameterStatus.newBuilder().setAcquisitionStatus(AcquisitionStatus.ACQUIRED).build();
        Map<Long, CacheEntry> m = new ConcurrentHashMap<>();
        long lastEviction;
        
        public ParameterStatus get(long expireMills) {
            if(expireMills<=0) {
                return ACQUIRED_NO_EXP;
            }
            long now = System.currentTimeMillis();
            
            if(now>lastEviction+EVICTION_INTERVAL) {
                performEviction();
            }
            CacheEntry ce = m.get(expireMills);
            if(ce==null) {
                ParameterStatus status = ParameterStatus.newBuilder().setAcquisitionStatus(AcquisitionStatus.ACQUIRED)
                        .setExpireMillis(expireMills).build();
                ce = new CacheEntry(status);
            }
            ce.lastAccessedTime = now;
            return ce.status;
        }
        
        private synchronized void performEviction() {
            long now = System.currentTimeMillis();
            if (now<lastEviction+EVICTION_INTERVAL) {
                return;
            }
            this.lastEviction = now;
            Iterator<Map.Entry<Long, CacheEntry>> it = m.entrySet().iterator();
            while(it.hasNext()) {
                Map.Entry<Long, CacheEntry> e = it.next();
                if(now > e.getValue().lastAccessedTime+CACHE_TIME) {
                    it.remove();
                }
            }
        }
        
        static class CacheEntry {
            final ParameterStatus status;
            long lastAccessedTime;
            CacheEntry(ParameterStatus status) {
                this.status = status;
            }
        }
    }
}
