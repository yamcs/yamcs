package org.yamcs.http.api;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.NotFoundException;
import org.yamcs.management.LinkManager;
import org.yamcs.protobuf.AbstractSdlsApi;
import org.yamcs.protobuf.GetSeqCtrRequest;
import org.yamcs.protobuf.GetSeqCtrResponse;
import org.yamcs.protobuf.SetKeyRequest;
import org.yamcs.protobuf.SetSeqCtrRequest;
import org.yamcs.protobuf.SetSpiRequest;
import org.yamcs.protobuf.SetSpisRequest;
import org.yamcs.security.SdlsSecurityAssociation;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.tctm.Link;
import org.yamcs.tctm.ccsds.UdpTcFrameLink;
import org.yamcs.tctm.ccsds.UdpTmFrameLink;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;

public class SdlsApi extends AbstractSdlsApi<Context> {
    private static final Logger log = LoggerFactory.getLogger(SdlsApi.class);
    private static YamcsServerInstance verifyInstanceObj(String instance) {
        YamcsServerInstance ysi = YamcsServer.getServer().getInstance(instance);
        if (ysi == null) {
            throw new NotFoundException("No instance named '" + instance + "'");
        }
        return ysi;
    }

    private Link verifyLink(Context ctx, String instance, String linkName) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlLinks);

        LinkManager lmgr = verifyInstanceObj(instance).getLinkManager();
        Link link = lmgr.getLink(linkName);

        if (link == null) {
            throw new NotFoundException("No such link");
        }

        if (link.isDisabled()) {
            throw new BadRequestException("Link unavailable");
        }
        return link;
    }

    private SdlsSecurityAssociation getSa(Link link, short spi) {
        SdlsSecurityAssociation maybeSdls;
        if (link instanceof UdpTmFrameLink l) {
            maybeSdls = l.getSdls(spi);
        } else if (link instanceof UdpTcFrameLink l) {
            maybeSdls = l.getSdls(spi);
        } else {
            throw new BadRequestException(String.format("Link %s is not a UDP TM or TC frame link",
                    link.getName()));
        }
        if (maybeSdls == null) {
            throw new BadRequestException(String.format("No SDLS SA found with SPI %s on link %s",
                    spi, link.getName()));
        }
        return maybeSdls;
    }

    @Override
    public void getSeqCtr(Context ctx, GetSeqCtrRequest request, Observer<GetSeqCtrResponse> observer) {
        String instance = request.getInstance();
        String linkName = request.getLinkName();
        short spi = (short) request.getSpi();
        Link link = verifyLink(ctx, instance, linkName);
        SdlsSecurityAssociation sdls = getSa(link, spi);

        byte[] seqNumBigEndian = sdls.getSeqNum();
        ByteString bs = ByteString.copyFrom(seqNumBigEndian);
        GetSeqCtrResponse.Builder gscrb = GetSeqCtrResponse.newBuilder().setSeq(bs);
        observer.complete(gscrb.build());
    }

    @Override
    public void setSeqCtr(Context ctx, SetSeqCtrRequest request, Observer<Empty> observer) {
        String instance = request.getInstance();
        String linkName = request.getLinkName();
        short spi = (short) request.getSpi();
        Link link = verifyLink(ctx, instance, linkName);
        SdlsSecurityAssociation sdls = getSa(link, spi);

        byte[] seqNumBigEndian = request.getBody().getSeq().toByteArray();
        sdls.setSeqNum(seqNumBigEndian);
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void setKey(Context ctx, SetKeyRequest request, Observer<Empty> observer) {
        String instance = request.getInstance();
        String linkName = request.getLinkName();
        short spi = (short) request.getSpi();
        Link link = verifyLink(ctx, instance, linkName);

        SdlsSecurityAssociation sdls = getSa(link, spi);

        byte[] newKey = request.getData().getData().toByteArray();
        if (newKey.length != 32) {
            throw new BadRequestException(String.format("AES-256-GCM expects a 256-bit key, %s bits provided",
                    newKey.length * 8));
        }
        sdls.setSecretKey(newKey);
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void setSpi(Context ctx, SetSpiRequest request, Observer<Empty> observer) {
        String instance = request.getInstance();
        String linkName = request.getLinkName();
        int vcId = request.getVcId();
        Link link = verifyLink(ctx, instance, linkName);
        int intSpi = request.getBody().getSpi();
        if (intSpi > Short.MAX_VALUE || intSpi < Short.MIN_VALUE) {
            throw new BadRequestException(String.format("Received SPI %d does not fit into a short, max value is %d",
                    Short.MAX_VALUE));
        }
        short spi = (short) intSpi;

        if (link instanceof UdpTmFrameLink l) {
            l.setSpis(vcId, new short[]{spi});
        } else if (link instanceof UdpTcFrameLink l) {
            l.setSpi(vcId, spi);
        } else {
            throw new BadRequestException(String.format("Link %s is not a UDP TM or TC frame link",
                    link.getName()));
        }
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void setSpis(Context ctx, SetSpisRequest request, Observer<Empty> observer) {
        String instance = request.getInstance();
        String linkName = request.getLinkName();
        int vcId = request.getVcId();
        Link link = verifyLink(ctx, instance, linkName);
        if (link instanceof UdpTmFrameLink l) {
            List<Integer> intSpis = request.getBody().getSpisList();
            short[] spis = new short[intSpis.size()];
            for (int i = 0; i < intSpis.size(); ++i) {
                int intSpi = intSpis.get(i);
                if (intSpi > Short.MAX_VALUE || intSpi < Short.MIN_VALUE) {
                    throw new BadRequestException(String.format("Received SPI %d does not fit into a short, max value is %d",
                            Short.MAX_VALUE));
                }
                spis[i] = (short) intSpi;
            }
            l.setSpis(vcId, spis);
        } else {
            throw new BadRequestException(String.format("Link %s is not a UDP TM frame link, cannot use multiple SPIs",
                    link.getName()));
        }
        observer.complete(Empty.getDefaultInstance());
    }
}