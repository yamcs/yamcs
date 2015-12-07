package org.yamcs.web.rest.archive;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Pvalue.TimeSeries;
import org.yamcs.protobuf.SchemaPvalue;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ParameterReplayRequest;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.utils.ParameterFormatter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.rest.BadRequestException;
import org.yamcs.web.rest.InternalServerErrorException;
import org.yamcs.web.rest.NotFoundException;
import org.yamcs.web.rest.RestException;
import org.yamcs.web.rest.RestParameterReplayListener;
import org.yamcs.web.rest.RestReplayListener;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequestHandler;
import org.yamcs.web.rest.RestResponse;
import org.yamcs.web.rest.archive.RestDownsampler.Sample;
import org.yamcs.web.rest.mdb.MissionDatabaseHelper;
import org.yamcs.web.rest.mdb.MissionDatabaseHelper.MatchResult;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.SystemParameterDb;

import com.google.protobuf.MessageLite;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;

public class ArchiveParameterRequestHandler extends RestRequestHandler {
    
    private static final Logger log = LoggerFactory.getLogger(ArchiveParameterRequestHandler.class);

    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        MatchResult<Parameter> mr = MissionDatabaseHelper.matchParameterName(req, pathOffset);
        if (!mr.matches()) {
            throw new NotFoundException(req);
        }
        
        pathOffset = mr.getPathOffset();
        if (req.hasPathSegment(pathOffset)) {
            switch (req.getPathSegment(pathOffset)) {
            case "samples":
                return getParameterSamples(req, mr.getRequestedId(), mr.getMatch());
            default:
                throw new NotFoundException(req, "No resource '" + req.getPathSegment(pathOffset) + "' for parameter " + mr.getRequestedId());
            }
        } else {
            return listParameterHistory(req, mr.getRequestedId());
        }
    }

    /**
     * A series is a list of samples that are determined in one-pass while processing a stream result.
     * Final API unstable.
     * <p>
     * If no query parameters are defined, the series covers *all* data.
     */
    private RestResponse getParameterSamples(RestRequest req, NamedObjectId id, Parameter p) throws RestException {
        ParameterType ptype = p.getParameterType();
        if (ptype == null) {
            throw new BadRequestException("Requested parameter has no type");
        } else if (!(ptype instanceof FloatParameterType) && !(ptype instanceof IntegerParameterType)) {
            throw new BadRequestException("Only integer or float parameters can be sampled. Got " + ptype.getClass());
        }
        
        ReplayRequest.Builder rr = ReplayRequest.newBuilder().setEndAction(EndAction.QUIT);
        rr.setParameterRequest(ParameterReplayRequest.newBuilder().addNameFilter(id));
        rr.setSpeed(ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP));
        
        if (req.hasQueryParameter("start")) {
            rr.setStart(req.getQueryParameterAsDate("start"));
        }
        rr.setStop(req.getQueryParameterAsDate("stop", TimeEncoding.getWallclockTime()));
        
        RestDownsampler sampler = new RestDownsampler(rr.getStop());
        
        RestReplays.replayAndWait(req, rr.build(), new RestReplayListener() {

            @Override
            public void onNewData(ProtoDataType type, MessageLite data) {
                ParameterData pdata = (ParameterData) data;
                for (ParameterValue pval : pdata.getParameterList()) {
                    switch (pval.getEngValue().getType()) {
                    case DOUBLE:
                        sampler.process(pval.getGenerationTime(), pval.getEngValue().getDoubleValue());
                        break;
                    case FLOAT:
                        sampler.process(pval.getGenerationTime(), pval.getEngValue().getFloatValue());
                        break;
                    case SINT32:
                        sampler.process(pval.getGenerationTime(), pval.getEngValue().getSint32Value());
                        break;
                    case SINT64:
                        sampler.process(pval.getGenerationTime(), pval.getEngValue().getSint64Value());
                        break;
                    case UINT32:
                        sampler.process(pval.getGenerationTime(), pval.getEngValue().getUint32Value()&0xFFFFFFFFL);
                        break;
                    case UINT64:
                        sampler.process(pval.getGenerationTime(), pval.getEngValue().getUint64Value());
                        break;
                    default:
                        log.warn("Unexpected value type " + pval.getEngValue().getType());
                    }
                }
            }
        });
        
        TimeSeries.Builder series = TimeSeries.newBuilder();
        for (Sample s : sampler.collect()) {
            series.addSample(ArchiveHelper.toGPBSample(s));
        }
        
        return new RestResponse(req, series.build(), SchemaPvalue.TimeSeries.WRITE);
    }
    
    private RestResponse listParameterHistory(RestRequest req, NamedObjectId id) throws RestException {
        long pos = req.getQueryParameterAsLong("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);
        boolean noRepeat = req.getQueryParameterAsBoolean("norepeat", false);
        
        // syspar provider is not currently added to replay channels, so it only generates errors
        if (SystemParameterDb.isSystemParameter(id)) {
            return new RestResponse(req, ParameterData.newBuilder().build(), SchemaPvalue.ParameterData.WRITE);
        }
        
        ReplayRequest rr = ArchiveHelper.toParameterReplayRequest(req, id, true);

        if (req.asksFor(CSV_MIME_TYPE)) {
            ByteBuf buf = req.getChannelHandlerContext().alloc().buffer();
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new ByteBufOutputStream(buf)))) {
                List<NamedObjectId> idList = Arrays.asList(id);
                ParameterFormatter csvFormatter = new ParameterFormatter(bw, idList);
                limit++; // Allow one extra line for the CSV header
                RestParameterReplayListener replayListener = new RestParameterReplayListener(pos, limit) {

                    @Override
                    public void onParameterData(ParameterData pdata) {
                        try {
                            csvFormatter.writeParameters(pdata.getParameterList());
                        } catch (IOException e) {
                            log.error("Error while writing parameter line", e);
                        }
                    }
                };
                replayListener.setNoRepeat(noRepeat);
                RestReplays.replayAndWait(req, rr, replayListener);
            } catch (IOException e) {
                throw new InternalServerErrorException(e);
            }
            return new RestResponse(req, CSV_MIME_TYPE, buf);
        } else {
            ParameterData.Builder resultb = ParameterData.newBuilder();
            RestParameterReplayListener replayListener = new RestParameterReplayListener(pos, limit) {
                
                @Override
                public void onParameterData(ParameterData pdata) {
                    resultb.addAllParameter(pdata.getParameterList());
                }
            };
            replayListener.setNoRepeat(noRepeat);
            RestReplays.replayAndWait(req, rr, replayListener);
            return new RestResponse(req, resultb.build(), SchemaPvalue.ParameterData.WRITE);
        }
    }
}
