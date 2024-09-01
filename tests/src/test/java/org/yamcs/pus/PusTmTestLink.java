package org.yamcs.pus;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.AbstractTmDataLink;
import org.yamcs.utils.TimeEncoding;

public class PusTmTestLink extends AbstractTmDataLink {

        @Override
        public void init(String yamcsInstance, String linkName, YConfiguration config) {
            super.init(yamcsInstance, linkName, config);
            System.out.println("in PusTmTestLink init");
        }


        public void generateEvent1(int subtype, short para1, short para2) {
            PusTmPacket pkt = new PusTmPacket(1, 5, 5, subtype);
            var bb = pkt.getUserDataBuffer();
            bb.put((byte) 1);// event_id
            bb.putShort(para1);
            bb.putShort(para2);

            process(pkt);
        }

        public void generateEvent2(int subtype, float para3) {
            PusTmPacket pkt = new PusTmPacket(1, 5, 5, subtype);
            var bb = pkt.getUserDataBuffer();
            bb.put((byte) 2);// event_id
            bb.putFloat(para3);

            process(pkt);
        }

        void process(PusTmPacket pusPkt) {
            long now = TimeEncoding.getWallclockTime();
            TmPacket pkt = new TmPacket(now, pusPkt.getBytes());
            pkt.setGenerationTime(now);
            processPacket(pkt);
        }

        @Override
        protected Status connectionStatus() {
            return Status.OK;
        }

        @Override
        protected void doStart() {
            notifyStarted();
        }

        @Override
        protected void doStop() {
            notifyStopped();
        }
    }