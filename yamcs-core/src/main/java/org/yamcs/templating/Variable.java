package org.yamcs.templating;

import java.util.List;

public abstract class Variable<T> {

    private String name;
    private boolean required;
    private String description;
    private List<T> choices;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
