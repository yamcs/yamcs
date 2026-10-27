package org.yamcs.tctm.ccsds.error;


public class Crc16McrfWord {

    // Reflected polynomial of 0x1021 = 0x8408
    private final int initialValue;
    private int crcValue;

    public Crc16McrfWord(int initialValue) {
        this.initialValue = initialValue;
    }

    /**
     * Accumulate the CRC by adding one char at a time.
     *
     * The checksum function adds the hash of one char at a time to the 16 bit
     * checksum (uint16_t).
     *
     * @param data new char to hash
     **/
    public void update_checksum(int data) {
        data = data & 0xff; //cast because we want an unsigned type
        int tmp = data ^ (crcValue & 0xff);
        tmp ^= (tmp << 4) & 0xff;
        crcValue = ((crcValue >> 8) & 0xff) ^ (tmp << 8) ^ (tmp << 3) ^ ((tmp >> 4) & 0xf);
    }

    public int getCurCrcValue() { 
        return crcValue;
    }

    /**
     * Initialize the buffer for the CRC16/MCRF4XX
     */
    public void start_checksum() {
        crcValue = initialValue;
    }

    public int getMSB() {
        return (((short) crcValue >> 8) & 0xff);
    }

    public int getLSB() {
        return ((short) crcValue & 0xff);
    }
}