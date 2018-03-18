package org.yamcs.web.rest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.yamcs.api.MediaType;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.web.HttpException;

import com.google.protobuf.util.JsonFormat;

import io.netty.buffer.ByteBufOutputStream;

/**
 * Facilitates working with chunked transfer of gpb parameters
 */
public class ParameterReplayToChunkedProtobufEncoder extends ParameterReplayToChunkedTransferEncoder {

    public ParameterReplayToChunkedProtobufEncoder(RestRequest req) throws HttpException {
        this(req, null);
    }

    public ParameterReplayToChunkedProtobufEncoder(RestRequest req, String filename) throws HttpException {
        super(req, req.deriveTargetContentType(), null);
    }

    @Override
    public void processParameterData(List<ParameterValueWithId> params, ByteBufOutputStream bufOut) throws IOException {
        if (MediaType.PROTOBUF.equals(contentType)) {
            ParameterData.Builder pd = ParameterData.newBuilder();
            for (ParameterValueWithId pvalid : params) {
                ParameterValue pval = pvalid.toGbpParameterValue();
                pd.addParameter(pval);
            }
            pd.build().writeDelimitedTo(bufOut);
        } else {
            for (ParameterValueWithId pvalid : params) {
                ParameterValue pval = pvalid.toGbpParameterValue();
                String json = JsonFormat.printer().print(pval);
                bufOut.write(json.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
}
