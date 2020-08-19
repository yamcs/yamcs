package org.yamcs.templating;

import java.util.List;

public class Variable {

    private String name;
    private String label;
    private boolean required;
    private String help;
    private String initial;
    private List<String> choices;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getHelp() {
        return help;
    }

    public void setHelp(String help) {
        this.help = help;
    }

    public String getInitial() {
        return initial;
    }

    public void setInitial(String initial) {
        this.initial = initial;
    }

    /**
     * Different choices. If a non-null result is returned, this variable should be chosen from a dropdown input. This
     * is evaluated each time a user is about to create an instance.
     */
    public List<String> getChoices() {
        return choices;
    }

    public void setChoices(List<String> choices) {
        this.choices = choices;
    }
}
