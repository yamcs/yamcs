package org.yamcs.simulator.pus;

import static org.yamcs.simulator.pus.PusSimulator.PUS_SUBTYPE_NACK_START;
import static org.yamcs.simulator.pus.PusSimulator.nack;

import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Pus11Service {
    private static final Logger log = LoggerFactory.getLogger(Pus11Service.class);

    final PusSimulator pusSimulator;
    final ScheduledThreadPoolExecutor executor;

    int count;
    boolean enabled = true;

    Pus11Service(PusSimulator pusSimulator) {
        this.pusSimulator = pusSimulator;
        this.executor = pusSimulator.executor;
    }

    public void executeTc(PusTcPacket tc) {
        switch (tc.getSubtype()) {
        // enable
        case 1 -> enabled = true;
        // disable
        case 2 -> enabled = false;
        // reset
        case 3 -> {
            // TODO: empty all queues
            enabled = false;
        }
        // INSERT
        case 4 -> {// TODO};

        }
        // delete by id
        case 5 -> {//TODO};

        }
        // delete by filter
        case 6 -> {
            // TODO
        }
        // time-shift by id
        case 7 -> {
            // TODO
        } // time-shfit by filter
        case 8 -> {
            // TODO
        }
        // detail report by id
        case 9 -> {
            // TODO
        }
        // detail report by filter
        case 11 -> {
            // TODO
        }
        // summary report by id
        case 12 -> {
            // TODO
        }
        // summary report by filter
        case 14 -> {
            // TODO
        }
        // time-shift all
        case 15 -> {
            // TODO
        } // detail report all
        case 16 -> {
            // TODO
        }
        // summary report all
        case 17 -> {
            // TODO
        }
        // report per sub-schedule
        case 18 -> {
            // TODO
        }
        // enable sub-schedule
        case 20 -> {
            // TODO
        }
        // disable sub-schedule
        case 21 -> {
            // TODO
        }
        // create group
        case 22 -> {
            // TODO
        }
        // delete group
        case 23 -> {
            // TODO
        }
        // enable group
        case 24 -> {
            // TODO
        }
        // disable group
        case 25 -> {
            // TODO
        }
        // report group
        case 26 -> {
            // TODO
        }
        }
    }
}
