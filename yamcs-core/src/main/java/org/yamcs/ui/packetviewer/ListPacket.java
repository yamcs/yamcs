package org.yamcs.ui.packetviewer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;

import org.yamcs.utils.CcsdsPacket;

/**
 * A packet appearing in the packet viewer list. Can be only partially loaded (i.e. the header only).
 */
public class ListPacket {
    private static final String HEX_CHARS = "0123456789abcdef"; 

    private String opsname;
    private long fileOffset;
    byte[] buf;
    int length;
    boolean incomplete=false;
    
    
    ListPacket(byte[] b, int length) {
        buf = b.clone();
        this.length = length;
    }

    ListPacket(byte[] buf, int length, long fileOffset) {
        this(buf, length);
        this.fileOffset = fileOffset;        
        this.incomplete = true;
    }
    
    public void setOpsname(String opsname)  {
        this.opsname = opsname;
    }

    @Override
    public String toString() {
        return opsname;
    }

    void load(File srcFile) throws IOException {
        if (incomplete) {
            FileInputStream reader = null;
            try {
                reader = new FileInputStream(srcFile);               
                byte[] data = new byte[length];
                reader.skip(fileOffset + buf.length);
                int remaining = length - buf.length;
                int res = reader.read(data, 16, remaining);
                if(res != remaining) throw new IOException("short read, expected "+remaining+", got "+res);
                System.arraycopy(buf, 0, data, 0, buf.length);                
                buf=data;
            } finally {
                if (reader != null) reader.close();
            }
            incomplete=false;
        }
    }

    void hexdump(StyledDocument hexDoc) {
        try {
            hexDoc.remove(0, hexDoc.getLength());

            for (int i = 0; i < buf.length; ) {
                // build one row of hexdump: offset, hex bytes, ascii bytes
                StringBuilder asciiBuf = new StringBuilder();
                StringBuilder hexBuf = new StringBuilder();
                hexBuf.append(HEX_CHARS.charAt(i>>12));
                hexBuf.append(HEX_CHARS.charAt((i>>8) & 0x0f));
                hexBuf.append(HEX_CHARS.charAt((i>>4) & 0x0f));
                hexBuf.append(HEX_CHARS.charAt(i & 0x0f));
                hexBuf.append(' ');

                for (int j = 0; j < 16; ++j, ++i) {
                    if (i < buf.length) {
                        byte b = buf[i];
                        hexBuf.append(HEX_CHARS.charAt((b>>4) & 0x0f));
                        hexBuf.append(HEX_CHARS.charAt(b & 0x0f));
                        if ((j & 1) == 1) hexBuf.append(' ');
                        char c = (b < 32) || (b > 126) ? '.' : (char)b;
                        asciiBuf.append(c);
                    } else {
                        hexBuf.append((j & 1) == 1 ? "   " : "  ");
                        asciiBuf.append(' ');
                    }
                }

                hexBuf.append(asciiBuf);
                hexBuf.append('\n');
                hexDoc.insertString(hexDoc.getLength(), hexBuf.toString(), hexDoc.getStyle("fixed"));
            }
        } catch (BadLocationException x) {
            System.err.println("cannot format hexdump of "+opsname+": "+x.getMessage());
        }
    }

    public long getInstant() {
        return CcsdsPacket.getInstant(buf);
    }

    public int getAPID() {      
        return CcsdsPacket.getAPID(buf);
    }

    public int getLength() {        
        return length;
    }

    public int getPacketID() {
        return CcsdsPacket.getPacketID(buf);
    }

    public byte[] getBuffer() {
        return buf;
    }
}
