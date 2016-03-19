package org.yamcs.ui.eventviewer;

import java.util.Observable;
import java.util.Observer;
import java.util.Vector;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.table.AbstractTableModel;

import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.ui.eventviewer.FilteringRule.FilteringRuleFactory;

/**
 * Table with filtering rules. The rules are matched from top to bottom, there
 * is always default rule. If none of the rules does apply for the event, then
 * the default rule is used. All rules are stored in the Preferences backing
 * store.
 */
public class FilteringRulesTable extends AbstractTableModel {
    /** Raw list of filtering rules */
    private Vector<FilteringRule> rules                   = null;

    /** Storage of application preferences */
    private Preferences           prefs                   = null;

    /** Key into the storage - number of saved filtering rules */
    private static final String   FILTERING_RULES_NUM_KEY = "/Filtering/NoOfRules";

    /** Table columns */
    static final String[]         columnNames             = { "Active", "Rule name", "Source", "Event Type", "Event Message", "Severity", "Alert", "Show" };

    /** Observable implementation */
    class TheObservable extends Observable {   
        /**
         * Signal change.
         */
        public void forceChanged() {
            setChanged();
        }
        
        /**
         * Notify all observers.
         */
        public void doNotifyAllObservers() {
            notifyObservers();
        }
    }

    /**
     * Observable - I have to use aggregation because no multiply inheritance is
     * possible
     */
    private TheObservable     theObservable       = null;

    /** Table column indices */
    public static final int ACTIVE_COL        = 0;
    public static final int RULE_NAME_COL     = 1;
    public static final int SOURCE_COL        = 2;
    public static final int EVENT_TYPE_COL    = 3;
    public static final int EVENT_MESSAGE_COL = 4;
    public static final int SEVERITY_COL      = 5;
    public static final int ALERT_COL         = 6;
    public static final int SHOW_COL          = 7;

    /**
     * Constructor.
     */
    public FilteringRulesTable() {
        prefs = Preferences.userNodeForPackage(this.getClass());
        theObservable = new TheObservable();
        readFilteringRules();
    }

    /**
     * Add observers.
     * @param observer Observer to be added.
     */
    public void registerObserver(Observer observer) {
        theObservable.addObserver(observer);
    }

    /**
     * Notify all observers
     */
    public void doNotifyAllObservers() {
        theObservable.doNotifyAllObservers();
    }
    
    /**
     * Add rule into the list of rules. New rule is added on the beginning of
     * the list.
     * 
     * @param rule Rule to be added
     */
    public void addRule(FilteringRule rule, int index) {
        rules.insertElementAt(rule, index);
        fireTableRowsInserted(index, index);
        theObservable.forceChanged();
        writeFilteringRules();
    }

    /**
     * Access to rule on the specified index.
     * @param index Index of the rule
     * @return Rule at the index.
     */
    public FilteringRule getRule(int index) {
        return rules.elementAt(index);
    }

    /**
     * Switch active status of the rule
     * @param index Index of the rule
     * @param active Status to be set.
     */
    public void switchRuleActivation(int index, boolean active) {
        getRule(index).setActive(active);
        theObservable.forceChanged();
        doNotifyAllObservers();
        writeFilteringRules();
    }
    
    /**
     * Number of rules in the table.
     * @return Number of rules in the table.
     */
    public int getSize()  {
        return rules.size();
    }

    /**
     * Remove rule on the index.
     * @param index Index of the rule to be removed
     */
    public void removeRule(int index)  {
        rules.remove(index);
        fireTableRowsDeleted(index, index);
        theObservable.forceChanged();
        writeFilteringRules();
    }

    /**
     * Access to the raw filtering table.
     * @return Vector with rules.
     */
    public Vector<FilteringRule> getRules()  {
        // this method should create deep copy somewhere in the future
        return rules;
    }

    /**
     * Switch rules on the specified positions.
     * @param xpos Position of the first rule
     * @param ypos Position of the second rule
     */
    public void switchRules(int xpos, int ypos) {
        FilteringRule tempRule = rules.set(xpos, rules.elementAt(ypos));
        rules.set(ypos, tempRule);

        // order so we can notify the table with valid range [xpos, ypos]
        if (xpos > ypos)
        {
            int temp = xpos;
            xpos = ypos;
            ypos = temp;
        }
        fireTableRowsUpdated(xpos, ypos);
        theObservable.forceChanged();

        writeFilteringRules();
    }

    /**
     * Obtain names of all filtering rules.
     * @return Vector containing names of all filtering rules in the same order
     *         as the rules are applied.
     */
    public Vector<String> getNames() {
        Vector<String> names = new Vector<String>();
        for (FilteringRule rule : rules)
        {
            names.add(rule.getName());
        }
        return names;
    }

    /**
     * Match event against the filtering table and find the rule which matches
     * that event. No other action is done, only the rule is determined. The
     * application of the rule is on the caller.
     * @param event Event matched against the filtering table
     * @return Rule matched by the event. Not null, always a rule is returned.
     */
    private FilteringRule matchFilteringRule(Event event) {
        for (FilteringRule rule : rules)
        {
            if (rule.isActive())
            {
                if (GlobUtils.isMatchGlob(rule.getSource(), event.getSource()) 
                        && GlobUtils.isMatchGlob(rule.getEventType(), event.getType()) 
                        && GlobUtils.isMatchGlob(rule.getEventText(), event.getMessage()) 
                        && rule.matchSeverity(event.getSeverity()))
                {
                    return rule;
                }
            }
        }

        // otherwise super default filtering rule
        return FilteringRuleFactory.createDefaultFilteringRule();
    }

    /**
     * Access to filtering rule on the specified index.
     * @param index Index of the rule
     * @return Rule on the specified index.
     */
    public FilteringRule getFilteringRule(int index) {
        return rules.elementAt(index);
    }

    /**
     * Query about the event visibility
     * @param event Event to be queried
     * @return True if event has to be shown
     */
    public boolean isEventVisible(Event event) {
        FilteringRule rule = matchFilteringRule(event);
        return rule.isShowOn();
    }

    /**
     * Store all filtering rules to storage.
     */
    public void writeFilteringRules() {
        try {
            // remove all old rules
            if (Preferences.userRoot().nodeExists("/Filtering"))  {
                Preferences.userRoot().node("/Filtering").removeNode();
            }

            prefs.putInt(FILTERING_RULES_NUM_KEY, rules.size());

            for (int i = 0; i < rules.size(); ++i) {
                String ruleNoKey = "/Filtering/Rule" + i;
                prefs.put(ruleNoKey + "/Name", rules.elementAt(i).getName());
                prefs.put(ruleNoKey + "/Source", rules.elementAt(i).getSource());
                prefs.put(ruleNoKey + "/EventType", rules.elementAt(i).getEventType());
                prefs.put(ruleNoKey + "/EventText", rules.elementAt(i).getEventText());
                prefs.put(ruleNoKey + "/AlertType", rules.elementAt(i).getAlertType().toString());
                prefs.putBoolean(ruleNoKey + "/IsShowOn", rules.elementAt(i).isShowOn());
                prefs.putBoolean(ruleNoKey + "/IsActive", rules.elementAt(i).isActive());
                prefs.put(ruleNoKey + "/Severity", rules.elementAt(i).getSeverityAsText());
            }
        } catch (BackingStoreException e) {
            prefs.putInt(FILTERING_RULES_NUM_KEY, 0);
        }
    }

    /**
     * Read particular filtering rule from storage
     * @param ruleNo Number of the rule
     * @return Filtering rule stored under the concrete number
     */
    private FilteringRule readFilteringRule(int ruleNo) {
        FilteringRule rule = new FilteringRule();

        String ruleNoKey = "/Filtering/Rule" + ruleNo;
        rule.setName(prefs.get(ruleNoKey + "/Name", ""));
        rule.setSource(prefs.get(ruleNoKey + "/Source", ""));
        rule.setEventType(prefs.get(ruleNoKey + "/EventType", ""));
        rule.setEventText(prefs.get(ruleNoKey + "/EventText", ""));
        rule.setAlertType(AlertSetting.valueOf(prefs.get(ruleNoKey + "/AlertType", "None")));
        rule.setShowOn(prefs.getBoolean(ruleNoKey + "/IsShowOn", false));
        rule.setActive(prefs.getBoolean(ruleNoKey + "/IsActive", false));
        rule.setSeverityFromText(prefs.get(ruleNoKey + "/Severity", "All"));

        return rule;
    }

    /**
     * Retrieve all filtering rules from storage.
     */
    private void readFilteringRules() {
        rules = new Vector<FilteringRule>();

        int rulesNo = getNumOfFilteringRules();

        for (int i = 0; i < rulesNo; ++i) {
            rules.add(readFilteringRule(i));
        }

        if (rules.size() == 0)  {
            // add default rules
            rules.add(FilteringRuleFactory.createWarningAndErrorsFilteringRule());
            rules.add(FilteringRuleFactory.createDefaultFilteringRule());
        }
        theObservable.forceChanged();
    }

    /**
     * Number of event filtering rules in storage.
     * @return Number of stored event filtering rules.
     */
    private int getNumOfFilteringRules() {
        return prefs.getInt(FILTERING_RULES_NUM_KEY, 0);
    }

    /**
     * 
     * @param event
     * @return 
     */
    public AlertType getAlertType(Event event) {
        AlertType alert = new AlertType();

        FilteringRule rule = matchFilteringRule(event);

        switch (rule.getAlertType())
        {
        case AlertNone:
            alert.alertPopup = false;
            alert.alertSound = false;
            break;
        case AlertPopUp:
            alert.alertPopup = true;
            alert.alertSound = false;
            break;
        case AlertSound:
            alert.alertPopup = false;
            alert.alertSound = true;
            break;
        default:
            alert.alertPopup = false;
            alert.alertSound = false;
            break;
        }

        return alert;
    }

    @Override
    public String getColumnName(int col) {
        return columnNames[col];
    }

    @Override
    public Class getColumnClass(int col) {
        if (col == FilteringRulesTable.ACTIVE_COL) {
            return Boolean.class;
        }
        return getValueAt(0, col).getClass();
    }

    @Override
    public int getRowCount() {
        return getSize();
    }

    @Override
    public int getColumnCount()  {
        return columnNames.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        FilteringRule rule = getFilteringRule(rowIndex);

        Object value = null;

        switch (columnIndex)
        {
        case FilteringRulesTable.ACTIVE_COL:
            value = rule.isActive();
            break;
        case FilteringRulesTable.RULE_NAME_COL:
            value = rule.getName();
            break;
        case FilteringRulesTable.SOURCE_COL:
            value = rule.getSource();
            break;
        case FilteringRulesTable.EVENT_TYPE_COL:
            value = rule.getEventType();
            break;
        case FilteringRulesTable.EVENT_MESSAGE_COL:
            value = rule.getEventText();
            break;
        case FilteringRulesTable.SEVERITY_COL:
            value = rule.getSeverityAsText();
            break;
        case FilteringRulesTable.ALERT_COL:
            value = rule.getAlertAsText();
            break;
        case FilteringRulesTable.SHOW_COL:
            value = rule.getShowAsText();
            break;
        default:
            break;
        }

        return value;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        FilteringRule rule = getFilteringRule(rowIndex);

        switch (columnIndex) {
        case FilteringRulesTable.ACTIVE_COL:
            rule.setActive((Boolean) aValue);
            break;
        case FilteringRulesTable.RULE_NAME_COL:
            rule.setName((String) aValue);
            break;
        case FilteringRulesTable.SOURCE_COL:
            rule.setSource((String) aValue);
            break;
        case FilteringRulesTable.EVENT_TYPE_COL:
            rule.setEventType((String) aValue);
            break;
        case FilteringRulesTable.EVENT_MESSAGE_COL:
            rule.setEventText((String) aValue);
            break;
        case FilteringRulesTable.SEVERITY_COL:
            rule.setSeverityFromText((String) aValue);
            break;
        case FilteringRulesTable.ALERT_COL:
            rule.setAlertFromText((String) aValue);
            break;
        case FilteringRulesTable.SHOW_COL:
            rule.setShowOn(((String) aValue).equalsIgnoreCase("Yes"));
            break;
        default:
            break;
        }

        writeFilteringRules();
        theObservable.forceChanged();
    }
}

