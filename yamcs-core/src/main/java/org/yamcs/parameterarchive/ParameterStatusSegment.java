package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.yamcs.parameter.BasicParameterValue;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.ParameterStatus;
import org.yamcs.utils.DecodingException;

import com.google.protobuf.InvalidProtocolBufferException;

public class ParameterStatusSegment extends ObjectSegment<ParameterStatus> {
    static ParameterStatusSerializer serializer = new ParameterStatusSerializer();
    static AcquiredCache cache = new AcquiredCache();

    public ParameterStatusSegment(boolean buildForSerialisation) {
        super(serializer, buildForSerialisation);
    }

    static public final ParameterStatus getStatus(BasicParameterValue pv, ParameterStatus prevStatus) {
        AcquisitionStatus acq = pv.getAcquisitionStatus();
        MonitoringResult mr = pv.getMonitoringResult();

        if (acq == AcquisitionStatus.ACQUIRED && mr == null) {
            return cache.get(pv.getExpireMills());
        }

        ParameterStatus newStatus = pv.getStatus().toProtoBuf();

        if (newStatus.equals(prevStatus)) {
            return prevStatus;
        }
        return newStatus;

    }

    public void insertParameterValue(int pos, BasicParameterValue pv) {
        ParameterStatus prevStatus = null;
        if (pos > 0) {
            prevStatus = get(pos - 1);
        }

        add(pos, getStatus(pv, prevStatus));
    }

    public void addParameterValue(BasicParameterValue pv) {
        ParameterStatus prevStatus = null;
        if (size > 0) {
            prevStatus = get(size - 1);
        }
        add(getStatus(pv, prevStatus));
    }

    public static ParameterStatusSegment parseFrom(ByteBuffer bb) throws DecodingException {
        ParameterStatusSegment r = new ParameterStatusSegment(false);
        r.parse(bb);
        return r;
    }

    static class ParameterStatusSerializer implements ObjectSerializer<ParameterStatus> {
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
     * cache to avoid creating unnecessary ParameterStatus objects for parameters that have no status other than
     * acquired and expiration time (likely 95% of all parameter values).
     *
     */
    static class AcquiredCache {
        static long EVICTION_INTERVAL = 3600000L;
        static long CACHE_TIME = 3600000L;

        static final ParameterStatus ACQUIRED_NO_EXP = ParameterStatus.newBuilder()
                .setAcquisitionStatus(AcquisitionStatus.ACQUIRED).build();
        Map<Long, CacheEntry> m = new ConcurrentHashMap<>();
        long lastEviction;

        public ParameterStatus get(long expireMills) {
            if (expireMills <= 0) {
                return ACQUIRED_NO_EXP;
            }
            long now = System.currentTimeMillis();

            if (now > lastEviction + EVICTION_INTERVAL) {
                performEviction();
            }
            CacheEntry ce = m.get(expireMills);
            if (ce == null) {
                ParameterStatus status = ParameterStatus.newBuilder().setAcquisitionStatus(AcquisitionStatus.ACQUIRED)
                        .setExpireMillis(expireMills).build();
                ce = new CacheEntry(status);
                m.put(expireMills, ce);
            }
            ce.lastAccessedTime = now;
            return ce.status;
        }

        private synchronized void performEviction() {
            long now = System.currentTimeMillis();
            if (now < lastEviction + EVICTION_INTERVAL) {
                return;
            }
            this.lastEviction = now;
            Iterator<Map.Entry<Long, CacheEntry>> it = m.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, CacheEntry> e = it.next();
                if (now > e.getValue().lastAccessedTime + CACHE_TIME) {
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

    public void insertGap(int pos) {
        // TODO Auto-generated method stub

    }
}
