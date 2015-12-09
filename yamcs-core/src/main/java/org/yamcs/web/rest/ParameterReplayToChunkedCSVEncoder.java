package org.yamcs.web.rest;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;

import org.yamcs.api.MediaType;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.ParameterFormatter;
import org.yamcs.web.HttpException;

import io.netty.buffer.ByteBufOutputStream;

/**
 * Facilitates working with chunked csv transfer of parameter data.
 * Wrap every ByteBufOutputStream with a ParameterFormatter
 */
public class ParameterReplayToChunkedCSVEncoder extends ParameterReplayToChunkedTransferEncoder {
    
    private List<NamedObjectId> idList;
    private ParameterFormatter formatter;
    
    public ParameterReplayToChunkedCSVEncoder(RestRequest req, List<NamedObjectId> idList) throws HttpException {
        super(req, MediaType.CSV);
        this.idList = idList;
        formatter.setWriteHeader(true);
    }
    
    @Override
    protected void resetBuffer() {
        super.resetBuffer();
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(bufOut));
        formatter = new ParameterFormatter(bw, idList, '\t');
        formatter.setWriteHeader(false);
    }
    
    @Override
    public void processParameterData(ParameterData pdata, ByteBufOutputStream bufOut) throws IOException {
        formatter.writeParameters(pdata.getParameterList());
    }
    
    @Override
    protected void closeBufferOutputStream() throws IOException {
        formatter.close();
    }
}
