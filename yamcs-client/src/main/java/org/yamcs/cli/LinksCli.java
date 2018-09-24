package org.yamcs.cli;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.api.InstanceClient;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.protobuf.Rest.EditLinkRequest;
import org.yamcs.protobuf.YamcsManagement.LinkInfo;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@Parameters(commandDescription = "Read and manipulate data links")
public class LinksCli extends Command {

    public LinksCli(Command parent) {
        super("links", parent);
        addSubCommand(new LinksList());
        addSubCommand(new LinksEnable());
        addSubCommand(new LinksDisable());
        addSubCommand(new LinksDescribe());
        setInstanceRequired(true);
    }

    @Parameters(commandDescription = "List links")
    class LinksList extends Command {

        public LinksList() {
            super("list", LinksCli.this);
        }

        @Override
        public void execute() throws Exception {
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();
            YamcsClient yamcsClient = new YamcsClient(ycp);
            InstanceClient instanceClient = yamcsClient.selectInstance(ycp.getInstance());
            instanceClient.getLinks().thenAccept(response -> {
                TableStringBuilder builder = new TableStringBuilder("NAME", "TYPE", "STREAM", "STATUS", "DATA COUNT");
                for (LinkInfo link : response.getLinkList()) {
                    builder.addLine(link.getName(), link.getType(), link.getStream(), link.getStatus(),
                            link.getDataCount());
                }
                console.println(builder.toString());
            }).get();
        }
    }

    @Parameters(commandDescription = "Enable a link")
    class LinksEnable extends Command {

        @Parameter(description = "link", required = true)
        List<String> links;

        public LinksEnable() {
            super("enable", LinksCli.this);
        }

        @Override
        public void execute() throws Exception {
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();
            YamcsClient yamcsClient = new YamcsClient(ycp);
            InstanceClient instanceClient = yamcsClient.selectInstance(ycp.getInstance());
            for (String link : links) {
                EditLinkRequest options = EditLinkRequest.newBuilder().setState("enabled").build();
                instanceClient.editLink(link, options).get();
            }
        }
    }

    @Parameters(commandDescription = "Disable a link")
    class LinksDisable extends Command {

        @Parameter(description = "link", required = true)
        List<String> links;

        public LinksDisable() {
            super("disable", LinksCli.this);
        }

        @Override
        public void execute() throws Exception {
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();
            YamcsClient yamcsClient = new YamcsClient(ycp);
            InstanceClient instanceClient = yamcsClient.selectInstance(ycp.getInstance());
            for (String link : links) {
                EditLinkRequest options = EditLinkRequest.newBuilder().setState("disabled").build();
                instanceClient.editLink(link, options).get();
            }
        }
    }

    @Parameters(commandDescription = "Describe link")
    class LinksDescribe extends Command {

        @Parameter(required = true, description = "instance")
        List<String> links = new ArrayList<>();

        public LinksDescribe() {
            super("describe", LinksCli.this);
        }

        @Override
        public void execute() throws Exception {
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();
            YamcsClient yamcsClient = new YamcsClient(ycp);
            InstanceClient instanceClient = yamcsClient.selectInstance(ycp.getInstance());
            for (String link : links) {
                instanceClient.getLink(link).thenAccept(response -> {
                    console.println(response.toString());
                }).get();
            }
        }
    }
}
