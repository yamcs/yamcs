package org.yamcs.http.api;

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
import org.yamcs.protobuf.ResetSeqCtrRequest;
import org.yamcs.protobuf.SetKeyRequest;
import org.yamcs.security.SdlsSecurityAssociation;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.tctm.Link;
import org.yamcs.tctm.ccsds.UdpTcFrameLink;
import org.yamcs.tctm.ccsds.UdpTmFrameLink;

import com.google.protobuf.Empty;

public class SdlsApi extends AbstractSdlsApi<Context> {
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

        int seqnum = sdls.getSeqNum();
        GetSeqCtrResponse.Builder gscrb = GetSeqCtrResponse.newBuilder().setSeq(seqnum);
        observer.complete(gscrb.build());
    }

    @Override
    public void resetSeqCtr(Context ctx, ResetSeqCtrRequest request, Observer<Empty> observer) {
        String instance = request.getInstance();
        String linkName = request.getLinkName();
        short spi = (short) request.getSpi();
        Link link = verifyLink(ctx, instance, linkName);
        SdlsSecurityAssociation sdls = getSa(link, spi);

        sdls.resetSeqNum();
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
}