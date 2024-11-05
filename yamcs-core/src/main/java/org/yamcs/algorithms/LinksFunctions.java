package org.yamcs.algorithms;

import org.yamcs.management.LinkManager;

/**
 * Library of functions available from within Algorithm scripts using this naming scheme:
 * <p>
 * The java method {@code LinksFunctions.[method]} is available in scripts as {@code Links.[method]}
 */
public class LinksFunctions {

    private final LinkManager linkManager;

    public LinksFunctions(LinkManager linkManager) {
        this.linkManager = linkManager;
    }

    public void enableLink(String linkName) {
        linkManager.enableLink(linkName);
    }

    public void disableLink(String linkName) {
        linkManager.disableLink(linkName);
    }
}