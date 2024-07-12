package org.yamcs.tctm.pus.services.tm.one;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;

public class ServiceOne implements PusService {

    public enum CcsdsApid {
        GROUND(0),
        AOCS(3),
        PROPULSION(4),
        SBAND(6),
        XBAND(7),
        EPS(12),
        AVIONICS(13),
        THERMAL(24),
        PAYLOAD(48),
        FSW_OBC(96),
        FSW_ARBITRATOR(97),
        FSW_TIC(98),
        IDLE(127);

        private final int value;

        CcsdsApid(int value) {
            this.value = value;
        }

        public static CcsdsApid fromValue(int value) {
            for (CcsdsApid enumValue : CcsdsApid.values()) {
                if (enumValue.value == value) {
                    return enumValue;
                }
            }
            return null;
        }

        public int getValue() {
            return value;
        }
    }


    protected enum FailureCode {
        BUTLER_TC_EXC_STATUS_SUCCESS(0),
        BUTLER_TC_EXC_STATUS_NULL_PTR_DETECTED(1),
        BUTLER_TC_EXC_STATUS_2_10_DEVICE_NOT_FOUND(2),
        BUTLER_TC_EXC_STATUS_3_SID_NOT_FOUND(3),
        BUTLER_TC_EXC_STATUS_9_INVALID_RATE_EXP(4),
        BUTLER_TC_EXC_STATUS_11_EMPTY_SCHEDULE(5),
        BUTLER_TC_EXC_STATUS_11_LIST_FULL(6),
        BUTLER_TC_EXC_STATUS_11_INVALID_TIMERANGE(7),
        BUTLER_TC_EXC_STATUS_11_TIMETAG_REPEATED(8),
        BUTLER_TC_EXC_STATUS_11_TIMETAG_NOT_FOUND(9),
        BUTLER_TC_EXC_STATUS_11_TIMERANGE_NOT_FOUND(10),
        BUTLER_TC_EXC_STATUS_13_RECEPTION_TIMER_INIT_FAILED(11),
        BUTLER_TC_EXC_STATUS_13_RECEPTION_TIMER_ACTIVATION_FAILED(12),
        BUTLER_TC_EXC_STATUS_13_RECEPTION_TIMER_DEACTIVATION_FAILED(13),
        BUTLER_TC_EXC_STATUS_13_ABORTED(14),
        BUTLER_TC_EXC_STATUS_13_SC_DISCONTINUITY(15),
        BUTLER_TC_EXC_STATUS_13_INVALID_TID(16),
        BUTLER_TC_EXC_STATUS_13_MEMORY_ERR(17),
        BUTLER_TC_EXC_STATUS_13_INVALID_REQ(18),
        BUTLER_TC_EXC_STATUS_6_INVALID_MEM_ID(19),
        BUTLER_TC_EXC_STATUS_6_INVALID_BASE_ID(20),
        BUTLER_TC_EXC_STATUS_6_CKSM_FAILED(21),
        BUTLER_STATUS_APID_NOT_SUPPORTED(22),
        BUTLER_STATUS_TC_PACKET_DECODING_FAILED(23);

        private final int value;
        
        FailureCode(int value) {
            this.value = value;
        }

        public static FailureCode fromValue(int value) {
            for (FailureCode enumValue : FailureCode.values()) {
                if (enumValue.value == value) {
                    return enumValue;
                }
            }
            return null;
        }

        public int getValue() {
            return value;
        }
    }

    Log log;
    Map<Integer, PusSubService> pusSubServices = new HashMap<>();
    String yamcsInstance;
    YConfiguration serviceOneConfig;

    public static final int DEFAULT_FAILURE_CODE_SIZE = 1;
    public static final int DEFAULT_FAILURE_DATA_SIZE = 4;
    public static final int REQUEST_ID_LENGTH = 4;

    public static int failureCodeSize;
    public static int failureDataSize;

    public ServiceOne(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        serviceOneConfig = config;

        failureCodeSize = config.getInt("failureCodeSize", DEFAULT_FAILURE_CODE_SIZE);
        failureDataSize = config.getInt("failureDataSize", DEFAULT_FAILURE_DATA_SIZE);

        initializeSubServices();
    }

    public void initializeSubServices() {
        pusSubServices.put(1, new SubServiceOne(yamcsInstance));
        pusSubServices.put(2, new SubServiceTwo(yamcsInstance));
        pusSubServices.put(7, new SubServiceSeven(yamcsInstance));
        pusSubServices.put(8, new SubServiceEight(yamcsInstance));
    }

    @Override
    public ArrayList<TmPacket> extractPusModifiers(TmPacket tmPacket) {
        return pusSubServices.get(PusTmCcsdsPacket.getMessageSubType(tmPacket.getPacket())).process(tmPacket);
    }

    @Override
    public PreparedCommand addPusModifiers(PreparedCommand telecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addPusModifiers'");
    }
}
