package org.yamcs.tctm;

import java.util.List;

public interface LinkActionProvider {

    List<LinkAction> getActions();

    LinkAction getAction(String actionId);
}
