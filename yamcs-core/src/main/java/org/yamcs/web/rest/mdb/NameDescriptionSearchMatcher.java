package org.yamcs.web.rest.mdb;

import java.util.Map.Entry;

import org.yamcs.xtce.NameDescription;

/**
 * Matches a search term with an XTCE name or any of the aliases
 */
class NameDescriptionSearchMatcher {

    private String[] terms;

    public NameDescriptionSearchMatcher(String searchTerm) {
        terms = searchTerm.toLowerCase().split("\\s+");
    }

    public boolean matches(NameDescription nameDescription) {
        for (String term : terms) {
            boolean match = false;
            if (nameDescription.getQualifiedName().toLowerCase().contains(term))
                continue;
            if (nameDescription.getAliasSet() != null) {
                for (Entry<String, String> entry : nameDescription.getAliasSet().getAliases().entrySet()) {
                    if (entry.getKey().toLowerCase().contains(term) || entry.getValue().toLowerCase().contains(term)) {
                        match = true;
                        break;
                    }
                }
            }
            if (!match)
                return false;
        }
        return true;
    }
}
