package org.yamcs.yarch;

public class TimePartitionInfo {
    private String dir;
    private long partitionStart;
    private long partitionEnd;

    public String getDir() {
        return dir;
    }
    public void setDir(String dir) {
        this.dir = dir;
    }
    public long getStart() {
        return partitionStart;
    }
    public void setStart(long partitionStart) {
        this.partitionStart = partitionStart;
    }
    public long getEnd() {
        return partitionEnd;
    }
    public void setEnd(long partitionEnd) {
        this.partitionEnd = partitionEnd;
    }
    
    
    @Override
    public String toString() {
        return "PartitionInfo [dir=" + dir + ", partitionStart="
                + partitionStart + ", partitionEnd=" + partitionEnd + "]";
    }
   
}