package org.yamcs.management;

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import org.yamcs.tctm.Link;

import org.yamcs.protobuf.YamcsManagement.LinkInfo;

public class LinkControlImpl extends StandardMBean implements LinkControl {
    Link link;
    LinkInfo linkInfo;

    public LinkControlImpl(String archiveInstance, String name, String streamName, String spec, Link link) throws NotCompliantMBeanException {
        super(LinkControl.class);
        this.link=link;
        linkInfo=LinkInfo.newBuilder().setInstance(archiveInstance)
                .setName(name).setStream(streamName)
                .setDisabled(link.isDisabled())
                .setStatus(link.getLinkStatus())
                .setDetailedStatus(link.getDetailedStatus())
                .setType(link.getClass().getSimpleName()).setSpec(spec)
                .setDataCount(link.getDataCount()).build();
    }

    LinkInfo getLinkInfo(){
        return linkInfo;
    }

    @Override
    public String getDetailedStatus() {
        return link.getDetailedStatus();
    }

    @Override
    public void disable() {
        link.disable();
    }

    @Override
    public void enable() {
        link.enable();
    }

    @Override
    public boolean isDisabled() {
        return link.isDisabled();
    }

    /**
     * @return true if the link status or datacount has changed since the creation or since the previous call of this method
     *  
     **/
    public boolean hasChanged() {
        if(!linkInfo.getStatus().equals(link.getLinkStatus())
                || linkInfo.getDisabled()!=link.isDisabled()
                || linkInfo.getDataCount()!=link.getDataCount()
                || !linkInfo.getDetailedStatus().equals(link.getDetailedStatus())) {

            linkInfo=LinkInfo.newBuilder(linkInfo).setDisabled(link.isDisabled())
                    .setStatus(link.getLinkStatus()).setDetailedStatus(link.getDetailedStatus())
                    .setDataCount(link.getDataCount()).build();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String getType() {
        return linkInfo.getType();
    }
}
