package org.yamcs.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;


public class PacketFormatter {
    private boolean withoutCcsds = false;
    private boolean withPacts = false, withArch = false;
    private boolean hex = false;
    private OutputStream out;
    private boolean onlyHeader;

    public PacketFormatter(OutputStream out) {
        this.out=out;
    }

    public void writePacket(CcsdsPacket c) throws IOException {
        ByteBuffer bb=c.getByteBuffer();

        if(withoutCcsds) bb.position(16);
        else bb.position(0);
        if(onlyHeader) {
            System.out.println("APID: "+CcsdsPacket.getAPID(bb)+", packetId: "+c.getPacketID()+" seqCount: "+CcsdsPacket.getSequenceCount(bb)+
                    ", gentime: "+TimeEncoding.toCombinedFormat(CcsdsPacket.getInstant(bb)));
        } else if(hex) {
            out.write(("apid: "+c.getAPID()+", seqCount: "+c.getSequenceCount()+", packetid: "+c.getPacketID()+"\n").getBytes());
            out.write(("time: "+TimeEncoding.toOrdinalDateTime(c.getInstant())+"\n").getBytes());
        }
        
        if(withPacts) { //add a fake pacts header
            ByteBuffer pactsMsgHdr = ByteBuffer.allocate(32);
            CcsdsPacket ccsds = new CcsdsPacket(bb);
            pactsMsgHdr.putInt(bb.limit() - 16); // without CCSDS header
            pactsMsgHdr.putInt(ccsds.getAPID() | 0x1000); // this is a PAYLOAD packet (not a SYSTEM packet)
            pactsMsgHdr.putInt(ccsds.getPacketID());
            pactsMsgHdr.putInt((int)ccsds.getCoarseTime());
            pactsMsgHdr.putInt(ccsds.getFineTime());
            pactsMsgHdr.rewind();
            if(hex) {
                while(pactsMsgHdr.position()<pactsMsgHdr.capacity()) {
                    out.write(String.format("%04x ",0xFFFF&pactsMsgHdr.getShort()).getBytes());
                    if(pactsMsgHdr.position()%16==0) out.write("\n".getBytes());
                }
                out.write("\n".getBytes());
            } else {
                out.write(pactsMsgHdr.array());
            }
        } else if (withArch) {
            // add an HRDP PathTM archive header (mutually exclusive with Pacts header)
            long now = Calendar.getInstance().getTimeInMillis();
            if (hex) {
                // TODO, or unnecessary. Archive headers are useful only when binary.
            } else {
                ByteBuffer arcHdr = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN);
                arcHdr.putInt(bb.limit() + 6); // 6 = rest of archive header
                arcHdr.put((byte)9); // constant
                arcHdr.order(ByteOrder.BIG_ENDIAN);
                arcHdr.putInt((int)(now / 1000 - 315964800)); // coarse time in GPS
                arcHdr.put((byte)((now % 1000) * 256 / 1000)); // fine time
                out.write(arcHdr.array());
            }
        }

        if(hex) {
            while(bb.position()<bb.capacity()) {
                out.write(String.format("%04x ",0xFFFF&bb.getShort()).getBytes());
                if(bb.position()%16==0) out.write("\n".getBytes());
            }
            out.write("\n".getBytes());
        } else {
            if(bb.hasArray()){
                out.write(bb.array(),bb.position(),bb.remaining());
            } else {
                byte[] b=new byte[bb.capacity()];
                bb.get(b);
                out.write(b);
            }
        }
    }
    public void setOnlyHeader(boolean b) {
        this.onlyHeader=b;
    }
    public void setHex(boolean hex) {
        this.hex = hex;
    }

    public void setWithoutCcsds(boolean withoutCcsds) {
        this.withoutCcsds = withoutCcsds;
    }

    public void setWithPacts(boolean withPacts) {
        this.withPacts = withPacts;
    }

    public void setWithArch(boolean withArch) {
        this.withArch = withArch;
    }

    public void close() throws IOException {
       out.close();
    }
}
