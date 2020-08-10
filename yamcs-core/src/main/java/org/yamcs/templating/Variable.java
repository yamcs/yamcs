package org.yamcs.templating;

import java.util.List;

public abstract class Variable<T> {

    private String name;
    private String verboseName;
    private boolean required;
    private String help;
    private List<T> choices;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVerboseName() {
        return verboseName;
    }

    public void setVerboseName(String verboseName) {
        this.verboseName = verboseName;
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

    /**
     * Different choices. If a non-null result is returned, this variable should be chosen from a dropdown input. This
     * is evaluated each time a user is about to create an instance.
     */
    public List<T> getChoices() {
        return choices;
    }

    public void setChoices(List<T> choices) {
        this.choices = choices;
    }
}
