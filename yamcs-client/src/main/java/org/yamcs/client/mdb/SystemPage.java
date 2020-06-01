package org.yamcs.client.mdb;

import java.util.List;

import org.yamcs.client.Page;

/**
 * A {@link Page} that also lists direct subsystems
 */
public interface SystemPage<T> extends Page<T> {

    List<String> getSubsystems();
}
