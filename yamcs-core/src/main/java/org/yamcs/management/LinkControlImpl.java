package org.yamcs.management;

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import org.yamcs.protobuf.YamcsManagement.LinkInfo;

public class LinkControlImpl extends StandardMBean implements LinkControl {
    LinkInfo linkInfo;

    public LinkControlImpl(LinkInfo linkInfo) throws NotCompliantMBeanException {
        super(LinkControl.class);
        this.linkInfo = linkInfo;
    }

    LinkInfo getLinkInfo(){
        return linkInfo;
    }

    @Override
    public String getDetailedStatus() {
        return linkInfo.getDetailedStatus();
    }

    @Override
    public void disable() {
        ManagementService.getInstance().disableLink(linkInfo.getInstance(), linkInfo.getName());
    }

    @Override
    public void enable() {
        ManagementService.getInstance().enableLink(linkInfo.getInstance(), linkInfo.getName());
    }

    @Override
    public boolean isDisabled() {
        return linkInfo.getDisabled();
    }

    @Override
    public String getType() {
        return linkInfo.getType();
    }
}
