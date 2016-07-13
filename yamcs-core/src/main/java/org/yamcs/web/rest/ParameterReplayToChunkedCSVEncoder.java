package org.yamcs.web.rest;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import org.yamcs.api.MediaType;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.ParameterFormatter;
import org.yamcs.web.HttpException;

import io.netty.buffer.ByteBufOutputStream;

/**
 * Facilitates working with chunked csv transfer of parameter data.
 * Wrap every ByteBufOutputStream with a ParameterFormatter
 */
public class ParameterReplayToChunkedCSVEncoder extends ParameterReplayToChunkedTransferEncoder {
    
    private ParameterFormatter formatter;
    
    public ParameterReplayToChunkedCSVEncoder(RestRequest req, List<NamedObjectId> idList) throws HttpException {
        super(req, MediaType.CSV, idList);
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
    public void processParameterData(List<ParameterValueWithId> params, ByteBufOutputStream bufOut) throws IOException {
        List<ParameterValue> pvlist = new ArrayList<>();
        for(ParameterValueWithId pvalid: params) {
            pvlist.add(pvalid.toGbpParameterValue());
        }
        formatter.writeParameters(pvlist);
    }
    
    @Override
    protected void closeBufferOutputStream() throws IOException {
        formatter.close();
    }
}
