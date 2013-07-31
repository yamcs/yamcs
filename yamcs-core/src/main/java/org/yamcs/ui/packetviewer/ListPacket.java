package org.yamcs.ui.packetviewer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;

import org.yamcs.utils.CcsdsPacket;

public class ListPacket extends CcsdsPacket {
    private static final String HEX_CHARS = "0123456789abcdef"; 

    private String opsname;
    private long fileOffset;

    ListPacket(ByteBuffer bb) {
        this(bb, -1);
    }

    ListPacket(ByteBuffer bb, long fileOffset) {
        super(bb);
        this.fileOffset = fileOffset;
    }
    
    public void setOpsname(String opsname)  {
        this.opsname = opsname;
    }

    @Override
    public String toString() {
        return opsname;
    }

    void load(File srcFile) throws IOException {
        if (bb.capacity() == 16) {
            FileInputStream reader = null;
            try {
                reader = new FileInputStream(srcFile);
                int len = getCccsdsPacketLength() + 7;
                byte[] data = new byte[len];
                reader.skip(fileOffset + 16);
                int res = reader.read(data, 16, len - 16);
                if(res!=len-16) throw new IOException("short read, expected "+(len-16)+", got "+res);
                bb.rewind();
                bb.get(data, 0, 16);
                bb = ByteBuffer.wrap(data);
            } finally {
                if (reader != null) reader.close();
            }
        }
    }

    void hexdump(StyledDocument hexDoc) {
        try {
            hexDoc.remove(0, hexDoc.getLength());

            for (int i = 0; i < bb.capacity(); ) {
                // build one row of hexdump: offset, hex bytes, ascii bytes
                StringBuilder asciiBuf = new StringBuilder();
                StringBuilder hexBuf = new StringBuilder();
                hexBuf.append(HEX_CHARS.charAt(i>>12));
                hexBuf.append(HEX_CHARS.charAt((i>>8) & 0x0f));
                hexBuf.append(HEX_CHARS.charAt((i>>4) & 0x0f));
                hexBuf.append(HEX_CHARS.charAt(i & 0x0f));
                hexBuf.append(' ');

                for (int j = 0; j < 16; ++j, ++i) {
                    if (i < bb.capacity()) {
                        byte b = bb.get(i);
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
}
