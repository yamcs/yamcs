package org.yamcs.ui.eventviewer;

import java.util.HashSet;
import java.util.Set;

import org.yamcs.protobuf.Yamcs.Event.EventSeverity;

/**
 * Filtering rule class. This simple class represents filtering rule. The name
 * of the filtering rule is just for user identification
 */
public class FilteringRule {
    // Removable from the filtering table
    private boolean removable = true;

    // Name of the rule
    private String name = "";

    // Active/inactive attribute
    private boolean isActive = true;

    // Source glob expression
    private String source = "*";

    // Event type glob expression
    private String eventType = "*";

    // event text glob expression
    private String eventText = "*";

    // alert type
    private AlertSetting alertType = AlertSetting.AlertNone;

    // show event option
    private boolean isShowOn = true;

    // event severity
    Set<EventSeverity>severities = new HashSet<>(); 
    
    /**
     * @return the removable
     */
    public boolean isRemovable() {
        return removable;
    }

    /**
     * @param removable
     *            the removable to set
     */
    public void setRemovable(boolean removable) {
        this.removable = removable;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name
     *            the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the isActive
     */
    public boolean isActive() {
        return isActive;
    }

    /**
     * @param isActive
     *            the isActive to set
     */
    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    /**
     * @return the source
     */
    public String getSource() {
        return source;
    }

    /**
     * @param source
     *            the source to set
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * @return the eventType
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * @param eventType
     *            the eventType to set
     */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    /**
     * @return the eventText
     */
    public String getEventText() {
        return eventText;
    }

    /**
     * @param eventText
     *            the eventText to set
     */
    public void setEventText(String eventText) {
        this.eventText = eventText;
    }

    /**
     * @return the alertType
     */
    public AlertSetting getAlertType() {
        return alertType;
    }

    /**
     * @param alertType
     *            the alertType to set
     */
    public void setAlertType(AlertSetting alertType) {
        this.alertType = alertType;
    }

    /**
     * @return the isShowOn
     */
    public boolean isShowOn() {
        return isShowOn;
    }

    /**
     * @param isShowOn
     *            the isShowOn to set
     */
    public void setShowOn(boolean isShowOn) {
        this.isShowOn = isShowOn;
    }

    /**
     * Match the severity level against the rule severity conditions.
     * 
     * @param severity
     *            Severity level to be matched.
     * @return True if the severity level matches the severity conditions of
     *         this rule.
     */
    public boolean matchSeverity(EventSeverity severity) {
      return severities.contains(severity);
    }

    /**
     * Copy data from other rule
     * 
     * @param other
     *            Other rule
     */
    public void copyFrom(FilteringRule other) {
        this.name = other.name;
        this.isActive = other.isActive;
        this.source = other.source;
        this.eventType = other.eventType;
        this.eventText = other.eventText;
        this.alertType = other.alertType;
        this.severities = new HashSet<>(other.severities);
        this.isShowOn = other.isShowOn;
    }

    /**
     * Obtain the string representation of the severity attribute.
     * 
     * @return String representation of the severity attribute
     */
    public String getSeverityAsText() {
        return severities.toString();
    }

    /**
     * Set the severity attributes according to text representation.
     * 
     * @param text
     *            Text representation of the severity setting.
     */
    public void setSeverityFromText(String text) {
    }

    /**
     * Set the severity attributes according to event severity.
     * 
     * @param eventSeverity
     *            Event severity
     */
    public void setSeverity(EventSeverity eventSeverity) {
        severities.clear();
        severities.add(eventSeverity);
    }

    /**
     * Obtain the string representation of the alert type attribute.
     * 
     * @return String representation of the alert type attribute.
     */
    public String getAlertAsText() {
        String text = "None";
        switch (alertType) {
        case AlertNone:
            text = "None";
            break;
        case AlertPopUp:
            text = "PopUp";
            break;
        case AlertSound:
            text = "Sound";
            break;
        default:
            text = "None";
            break;
        }
        return text;
    }

    /**
     * Set alert type from the text representation of the alert type.
     * 
     * @param text
     *            Text representation of the alert type.
     */
    public void setAlertFromText(String text) {
        if (text.equals("None")) {
            alertType = AlertSetting.AlertNone;
        } else if (text.equals("Sound")) {
            alertType = AlertSetting.AlertSound;
        } else {
            alertType = AlertSetting.AlertPopUp;
        }
    }

    /**
     * Obtain show attribute as text.
     * 
     * @return String representation of the show attribute.
     */
    public String getShowAsText() {
        return (isShowOn) ? "Yes" : "No";
    }

    /**
     * Factory class for creation of filtering rules
     * 
     */
    public static class FilteringRuleFactory {
        /**
         * Private constructor.
         */
        private FilteringRuleFactory() {

        }

        /**
         * Create default filtering rule - show all
         * 
         * @return Default filtering rule
         */
        public static FilteringRule createDefaultFilteringRule() {
            FilteringRule rule = new FilteringRule();
            rule.setName("Other");
            rule.setRemovable(false);
            return rule;
        }

        /**
         * Create default filtering rule for filtering Errors and Warnings
         * 
         * @return Warnings and errors filtering rule
         */
        public static FilteringRule createWarningAndErrorsFilteringRule() {
            FilteringRule rule = new FilteringRule();

            rule.setName("Warnings & Errors");
            rule.setSource("*");
            rule.setEventType("*");
            rule.setEventText("*");
            rule.setAlertType(AlertSetting.AlertNone);
            rule.setShowOn(true);
            rule.setActive(true);
            rule.setSeverityFromText("Warning & Error");
            rule.setRemovable(false);

            return rule;
        }
    }

}
