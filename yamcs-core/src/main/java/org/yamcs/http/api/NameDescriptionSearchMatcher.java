package org.yamcs.http.api;

import org.yamcs.xtce.NameDescription;

/**
 * Matches a search term with an XTCE name or any of the aliases
 */
public class NameDescriptionSearchMatcher {

    private String[] terms;
    private boolean searchDescription = true;

    public NameDescriptionSearchMatcher(String searchTerm) {
        terms = searchTerm.toLowerCase().split("\\s+");
    }

    public void setSearchDescription(boolean searchDescription) {
        this.searchDescription = searchDescription;
    }

    public boolean matches(String name) {
        for (String term : terms) {
            if (!name.toLowerCase().contains(term)) {
                return false;
            }
        }
        return true;
    }

    public boolean matches(NameDescription nameDescription) {
        for (String term : terms) {
            boolean match = false;
            if (nameDescription.getQualifiedName().toLowerCase().contains(term)) {
                match = true;
            } else if (searchDescription && nameDescription.getShortDescription() != null
                    && nameDescription.getShortDescription().toLowerCase().contains(term)) {
                match = true;
            } else if (nameDescription.getAliasSet() != null) {
                for (String alias : nameDescription.getAliasSet().getAliases().values()) {
                    if (alias.toLowerCase().contains(term)) {
                        match = true;
                        break;
                    }
                }
            }
            if (!match) {
                return false;
            }
        }
        return true;
    }
}
