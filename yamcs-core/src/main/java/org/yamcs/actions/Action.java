package org.yamcs.actions;

import java.util.HashSet;
import java.util.Set;

import org.yamcs.Spec;

import com.google.gson.JsonObject;

/**
 * @param <T>
 *            the type of the action target
 */
public abstract class Action<T> {

    public static enum ActionStyle {
        PUSH_BUTTON,
        CHECK_BOX,
    }

    private final String id;
    private final String label;
    private final ActionStyle style;
    private boolean enabled = true;
    private boolean checked = false;

    private Set<ActionChangeListener> changeListeners = new HashSet<>(1);

    public Action(String id, String label) {
        this(id, label, ActionStyle.PUSH_BUTTON);
    }

    public Action(String id, String label, ActionStyle style) {
        this.id = id;
        this.label = label;
        this.style = style;
    }

    /**
     * Returns a unique identifier for this action.
     */
    public String getId() {
        return id;
    }

    /**
     * Human-readable label for this action.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Specification of action arguments (if the actions supports or requires this)
     */
    public Spec getSpec() {
        return new Spec();
    }

    /**
     * Returns whether this action is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set the enabled state of this action.
     * <p>
     * An action that is not enabled, is visible, but can't be executed.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        changeListeners.forEach(ActionChangeListener::onChange);
    }

    /**
     * Return the style of this action.
     */
    public ActionStyle getStyle() {
        return style;
    }

    /**
     * Returns the checked state of this action.
     * <p>
     * Only relevant if the style of this action is set to {@link ActionStyle#CHECK_BOX}.
     */
    public boolean isChecked() {
        return checked;
    }

    /**
     * Sets the checked state of this action.
     * <p>
     * Only relevant if the style of this action is set to {@link ActionStyle#CHECK_BOX}.
     */
    public void setChecked(boolean checked) {
        this.checked = checked;
        changeListeners.forEach(ActionChangeListener::onChange);
    }

    /**
     * Add a listener that will get notified whenever one of the action properties has changed.
     */
    public void addChangeListener(ActionChangeListener listener) {
        changeListeners.add(listener);
    }

    /**
     * Remove a previously registered change listeners.
     */
    public void removeChangeListener(ActionChangeListener listener) {
        changeListeners.remove(listener);
    }

    /**
     * Execute an action.
     * <p>
     * When successful, use the {@code result} parameter to provide the action result (which may be null).
     * <p>
     * When an error occurs, use the {@code result} parameter to provide the exception.
     * 
     * @param target
     *            The target of this action
     * @param request
     *            Optional request options
     * @param result
     *            Object where to mark the successful end of an action.
     */
    public abstract void execute(T target, JsonObject request, ActionResult result);
}
