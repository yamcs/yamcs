package org.yamcs.filetransfer;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferState;
import org.yamcs.utils.TimeEncoding;

public class FileTransferFilter {

    public long start = TimeEncoding.INVALID_INSTANT;
    public long stop = TimeEncoding.INVALID_INSTANT;
    public TransferDirection direction;
    public Long localEntityId;
    public Long remoteEntityId;
    public List<TransferState> states = new ArrayList<>();
    public int limit = 100;
    public boolean descending = true;
}
