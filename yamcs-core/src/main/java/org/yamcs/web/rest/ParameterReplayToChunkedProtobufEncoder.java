package org.yamcs.web.rest;

import java.io.IOException;
import java.util.List;

import org.yamcs.api.MediaType;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.SchemaPvalue;
import org.yamcs.web.HttpException;

import com.fasterxml.jackson.core.JsonGenerator;

import io.netty.buffer.ByteBufOutputStream;
import io.protostuff.JsonIOUtil;

/**
 * Facilitates working with chunked transfer of gpb parameters
 */
public class ParameterReplayToChunkedProtobufEncoder extends ParameterReplayToChunkedTransferEncoder {
    
    public ParameterReplayToChunkedProtobufEncoder(RestRequest req) throws HttpException {
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
                JsonGenerator generator = req.createJsonGenerator(bufOut);
                JsonIOUtil.writeTo(generator, pval, SchemaPvalue.ParameterValue.WRITE, false);
                generator.close();
            }
        }
    }

}
