package org.yamcs.http.api;

import org.yamcs.xtce.NameDescription;

/**
 * Matches a search term with an XTCE name or any of the aliases
 */
public class NameDescriptionSearchMatcher {

    private String[] terms;

    public NameDescriptionSearchMatcher(String searchTerm) {
        terms = searchTerm.toLowerCase().split("\\s+");
    }

    public boolean matches(NameDescription nameDescription) {
        for (String term : terms) {
            boolean match = false;
            if (nameDescription.getQualifiedName().toLowerCase().contains(term)) {
                match = true;
            }
            if (nameDescription.getShortDescription() != null
                    && nameDescription.getShortDescription().toLowerCase().contains(term)) {
                match = true;
            }
            if (nameDescription.getAliasSet() != null) {
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
