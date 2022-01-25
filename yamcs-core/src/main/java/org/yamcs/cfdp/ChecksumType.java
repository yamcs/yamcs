package org.yamcs.cfdp;

public enum ChecksumType {
    MODULAR(0), PROXIMITY_CRC32(1), CRC32C(2), CRC32(3), NONE(15);
    
    final byte id;
    ChecksumType(int id) {
        this.id = (byte) id;
    }
    
    public byte id() {
        return id;
    }

    /**
     * 
     * @return the checksum type corresponding to the given id or null if it does not exist (invalid id)
     */
    public static ChecksumType fromId(int id) {
        for (ChecksumType ct : values()) {
            if (ct.id == id) {
                return ct;
            }
        }
        return null;
    }
}
