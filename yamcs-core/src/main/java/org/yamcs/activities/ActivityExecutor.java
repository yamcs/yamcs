package org.yamcs.activities;

import java.util.Map;

import org.yamcs.Spec;
import org.yamcs.Spec.NamedSpec;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.security.User;

/**
 * An executor capable of executing any amount of activities of a specified type.
 */
public interface ActivityExecutor {

    /**
     * The activity recognized by this executor
     */
    String getActivityType();

    /**
     * UI-friendly display name for this type of activity
     */
    String getDisplayName();

    /**
     * Short imperative description of this type of activity.
     */
    String getDescription();

    /**
     * Icon hint for UI purpose. This should be a name present in Material Icons.
     */
    String getIcon();

    /**
     * Specify the options for this executor.
     * <p>
     * The name of this spec, is where the options are to be defined as part of the {@link ActivityService}
     * configuration.
     * <p>
     * For example, the name for {@link ScriptExecutor} is {@code scriptExecution}. It is suggested to use the same
     * naming strategy.
     */
    NamedSpec getSpec();

    /**
     * Initialize this executor. Called when the activity service starts.
     */
    void init(ActivityService activityService, YConfiguration options);

    /**
     * Specify the allowed arguments of an activity
     */
    Spec getActivitySpec();

    /**
     * Return a short (one-line) descriptive text for an activity that would have the specified args.
     */
    String describeActivity(Map<String, Object> args);

    /**
     * Create an executable activity with the provided arguments
     * 
     * @param activity
     *            the activity to execute
     *
     * @param user
     *            the calling user
     */
    ActivityExecution createExecution(Activity activity, User user) throws ValidationException;
}
