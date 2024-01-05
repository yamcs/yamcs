package org.yamcs.tctm;

import org.yamcs.utils.TimeEncoding;

public class TmPackage {
    private byte[] pkg;
    private long gentime = TimeEncoding.INVALID_INSTANT; // generation time

    /**
     * The time when the package has been generated onboard.
     * 
     * @return
     */
    public long getGenerationTime() {
        return gentime;
    }

    public TmPackage(byte[] pkg, long gentime) {
        this.gentime = gentime;
        this.pkg = pkg;
    }

    public byte[] getPkg() {
        return this.pkg;
    }

}
