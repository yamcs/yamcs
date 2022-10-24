package org.yamcs.tctm;

import java.util.List;

public interface LinkActionProvider {

    void addAction(LinkAction action);

    List<LinkAction> getActions();

    LinkAction getAction(String actionId);
}
