package org.yamcs.tctm;

import java.util.Map;

import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.SystemParametersProducer;
import org.yamcs.parameter.SystemParametersService;

/**
 * A source of data into yamcs; Currently TM, TC and Parameter
 * 
 * @author nm
 *
 */
public interface Link {
    public enum Status {
        /**
         * the link is up ready to receive data.
         */
        OK,
        /**
         * the link is down although it should be up; for instance a TCP client that cannot connect to the remote
         * server.
         */
        UNAVAIL,
        /**
         * the link has been disabled by the user (so it's implicitly unavailable)
         */
        DISABLED,
        /**
         * the link has failed (like an internal crash while processing the data)
         */
        FAILED;
    }

    /**
     * Returns the current link status.
     */
    public Status getLinkStatus();

    /**
     * Returns a short detail status (one-line)
     */
    default String getDetailedStatus() {
        return null;
    }

    /**
     * Returns structured information, specific to the link.
     */
    default Map<String, Object> getExtraInfo() {
        return null;
    }

    /**
     * Reenable the data transit if disabled by the disable() method.
     */
    public void enable();

    /**
     * Disable any data I/O through this link. Any connection to a server is closed. Can be reenabled using the enable
     * method. Note that this method can be called before starting the service if it's configured as such in the
     * configuration file
     */
    public void disable();

    /**
     * return true if the link has been disabled by the user.
     * <p>
     * See also {@link #isEffectivelyDisabled()}
     */
    public boolean isDisabled();

    /**
     * return true if this link or its parent (in case of a sub-link part of an aggregated link) is disabled
     */
    public default boolean isEffectivelyDisabled() {
        if (isDisabled()) {
            return true;
        } else if (getParent() != null) {
            return getParent().isEffectivelyDisabled();
        } else {
            return false;
        }
    }

    public long getDataInCount();

    public long getDataOutCount();

    public void resetCounters();

    /**
     * Return the name of the link
     */
    public String getName();

    /**
     * 
     * @return the config (args) used when creating the link
     */
    public YConfiguration getConfig();

    /**
     * If this link is a sublink of an aggregated link, get the parent link.
     */
    default AggregatedDataLink getParent() {
        return null;
    }

    /**
     * Set the parent link if this is a sublink of an aggregated link.
     */
    default void setParent(AggregatedDataLink parent) {
    }

    /**
     * Called by the LinkManager before startup if the {@link SystemParametersService} service is enabled, to setup
     * necessary things for later parameter collection.
     * <p>
     * The method is called only on the links that implement the {@link SystemParametersProducer} interface; they are
     * also registered with the {@link SystemParametersService} to be called regularly after the start.
     */
    default void setupSystemParameters(SystemParametersService sysParamCollector) {
    }

    /**
     * Called at startup to initialize the link.
     * <p>
     * The config corresponds to the map that is under the link definition in yamcs.instance.yaml.
     * 
     * @param yamcsInstance
     * @param linkName
     * @param config
     *            - the configuration - cannot be null (but can be empty)
     */
    default void init(String yamcsInstance, String linkName, YConfiguration config) {
    }

    /**
     * Returns the valid configuration of the input args of this link.
     * 
     * @return the argument specification, or {@code null} if the args should not be validated.
     */
    public default Spec getSpec() {
        return null;
    }

    /**
     * Returns a default link {@link Spec}. This can be used in an implementation of {{@link #getSpec()}.
     * 
     * Eventually (after a few years), it is expected to migrate this logic directly into {Link{@link #getSpec()},
     * rather than returning null from there. But we want to give sufficient time for links everywhere to start defining
     * their arguments.
     */
    public default Spec getDefaultSpec() {
        Spec spec = new Spec();
        spec.addOption("name", OptionType.STRING).withRequired(true);
        spec.addOption("class", OptionType.STRING).withRequired(true);
        spec.addOption("stream", OptionType.STRING);
        spec.addOption("tcStream", OptionType.STRING);
        spec.addOption("tmStream", OptionType.STRING);
        spec.addOption("ppStream", OptionType.STRING);
        spec.addOption("enabledAtStartup", OptionType.BOOLEAN);
        spec.addOption("invalidPackets", OptionType.STRING).withChoices("DROP", "PROCESS", "DIVERT")
                .withDefault("DROP");
        spec.addOption("invalidPacketsStream", OptionType.STRING).withDefault("invalid_tm");

        spec.mutuallyExclusive("stream", "tcStream");
        spec.mutuallyExclusive("stream", "tmStream");
        spec.mutuallyExclusive("stream", "ppStream");
        return spec;
    }
}
