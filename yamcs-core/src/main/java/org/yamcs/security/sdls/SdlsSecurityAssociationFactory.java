package org.yamcs.security.sdls;

import org.yamcs.Spec;
import org.yamcs.YConfiguration;

/**
 * An interface for Security Association factories. A factory pattern is needed because a Security Association
 * constructor itself might take varying arguments, depending on the exact implementation.
 */
public interface SdlsSecurityAssociationFactory {
    /**
     * @param instanceName the Yamcs instance
     * @param linkName the link on which the SA is used
     * @param spi the SDLS Security Parameter Index, identifying the SA
     * @param args custom arguments to be used while constructing the SA
     * @return the Security Association
     */
    SdlsSecurityAssociation create(String instanceName, String linkName, short spi, YConfiguration args);

    /**
     * @return the Yamcs configuration spec
     */
    Spec getSpec();
}