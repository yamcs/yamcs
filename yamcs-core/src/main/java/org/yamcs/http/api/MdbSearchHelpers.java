package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.yamcs.utils.AggregateUtil;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.ArrayParameterType;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.PathElement;

public class MdbSearchHelpers {

    /**
     * Searches for entry matches inside a parameter.
     * <p>
     * The actual parameter is not included in the result. Only array entries (in case the searchTerm contains indexes),
     * and/or aggregate members.
     */
    public static List<EntryMatch> searchEntries(Parameter parameter, String searchTerm) {
        ParameterType ptype = parameter.getParameterType();
        if (ptype == null || (!(ptype instanceof AggregateParameterType) && !(ptype instanceof ArrayParameterType))) {
            return Collections.emptyList();
        }
        SearchTerm term = new SearchTerm(searchTerm);
        if (term.searchPath == null) {
            return Collections.emptyList();
        }

        return new Entry(parameter).findSubEntries().stream()
                .filter(entry -> entry.fillIndexes(term))
                .map(entry -> new EntryMatch(entry)).collect(Collectors.toList());
    }

    private static class SearchTerm {
        String term;
        PathElement[] searchPath;

        private SearchTerm(String term) {
            this.term = term.toLowerCase();

            var aggSep = AggregateUtil.findSeparator(this.term);
            if (aggSep >= 0) {
                var pname = this.term.substring(0, aggSep);
                try {
                    var memberSegments = AggregateUtil.parseReference(this.term.substring(aggSep));
                    this.searchPath = new PathElement[1 + memberSegments.length];
                    this.searchPath[0] = new PathElement(pname, null);
                    for (int i = 0; i < memberSegments.length; i++) {
                        this.searchPath[i + 1] = memberSegments[i];
                    }
                } catch (IllegalArgumentException e) {
                    // Ignore
                }
            } else {
                this.searchPath = new PathElement[] { new PathElement(this.term, null) };
            }
        }
    }

    private static class Entry {
        final Parameter parameter;
        final ParameterType ptype; // Type at the path offset
        PathElement[] path;

        Entry(Parameter parameter) {
            this(parameter, new PathElement[] { new PathElement(parameter.getQualifiedName(), null) },
                    parameter.getParameterType());
        }

        Entry(Parameter parameter, PathElement[] path, ParameterType ptype) {
            this.parameter = parameter;
            this.path = path;
            this.ptype = ptype;
        }

        Entry createSubEntry(String member, int[] index, ParameterType ptype) {
            PathElement[] subPath = Arrays.copyOf(path, path.length + 1);
            subPath[subPath.length - 1] = new PathElement(member, index);
            return new Entry(parameter, subPath, ptype);
        }

        /**
         * Finds entries beneath this entry in case the entry is an aggregate or an array. The returned entries contain
         * [] placeholders for array indexes.
         */
        private List<Entry> findSubEntries() {
            if (ptype instanceof AggregateParameterType) {
                List<Entry> entries = new ArrayList<>();
                for (Member member : ((AggregateParameterType) ptype).getMemberList()) {
                    Entry subEntry = createSubEntry(member.getName(), null, (ParameterType) member.getType());
                    entries.add(subEntry);
                    if (subEntry.ptype instanceof AggregateParameterType) {
                        entries.addAll(subEntry.findSubEntries());
                    }
                }
                return entries;
            } else if (ptype instanceof ArrayParameterType) {
                List<Entry> entries = new ArrayList<>();
                ParameterType elementType = (ParameterType) ((ArrayParameterType) ptype).getElementType();
                Entry subEntry = createSubEntry(null, new int[] { -1 } /* placeholder */, elementType);
                entries.add(subEntry);
                if (subEntry.ptype instanceof AggregateParameterType) {
                    entries.addAll(subEntry.findSubEntries());
                }
                return entries;
            } else {
                return Collections.emptyList();
            }
        }

        /**
         * Matches the search term while filling indexes with the ones from the request. Returns false if this did not
         * work (meaning: this entry is not matching)
         */
        boolean fillIndexes(SearchTerm term) {
            StringBuilder buf = new StringBuilder();
            boolean match = true;
            try (Scanner scanner = new Scanner(getQualifiedName())) {
                for (PathElement el : term.searchPath) {
                    String name = el.getName() != null ? el.getName() : "";
                    String needleRegex = "(.*" + Pattern.quote(name) + ")";
                    if (el.getIndex() != null) {
                        needleRegex += "\\[-1\\]";
                    }
                    String text = scanner.findInLine(Pattern.compile(needleRegex, Pattern.CASE_INSENSITIVE));
                    if (text == null) {
                        match = false;
                        break;
                    } else {
                        MatchResult result = scanner.match();
                        buf.append(result.group(1));
                        if (el.getIndex() != null) {
                            int[] index = el.getIndex();
                            for (int i = 0; i < index.length; i++) {
                                buf.append("[" + index[i] + "]");
                            }
                        }
                    }
                }
                if (match && scanner.hasNext()) {
                    buf.append(scanner.nextLine());
                }
            }

            if (match) {
                String result = buf.toString();
                if (!result.contains("[-1]")) { // Ignore deeper array offsets if the term did not pin them
                    var aggSep = AggregateUtil.findSeparator(result);
                    if (aggSep >= 0) {
                        var pname = result.substring(0, aggSep);
                        try {
                            var memberSegments = AggregateUtil.parseReference(result.substring(aggSep));
                            path = new PathElement[1 + memberSegments.length];
                            path[0] = new PathElement(pname, null);
                            for (int i = 0; i < memberSegments.length; i++) {
                                path[i + 1] = memberSegments[i];
                            }
                        } catch (IllegalArgumentException e) {
                            // Ignore
                        }
                    } else {
                        path = new PathElement[] { new PathElement(result, null) };
                    }

                    return true;
                }
            }
            return false;
        }

        String getQualifiedName() {
            StringBuilder buf = new StringBuilder();
            for (int i = 0; i < path.length; i++) {
                PathElement segment = path[i];
                if (i != 0 && segment.getName() != null) {
                    buf.append(".");
                }
                buf.append(segment);
            }
            return buf.toString();
        }
    }

    private static class Builder extends NameDescription.Builder<Builder> {
    }

    @SuppressWarnings("serial")
    static class EntryMatch extends NameDescription {
        public Parameter parameter;
        public PathElement[] entryPath;
        public ParameterType entryType;

        private EntryMatch(Entry entry) {
            super(new MdbSearchHelpers.Builder()
                    .setQualifiedName(entry.parameter.getQualifiedName()));
            this.entryPath = entry.path;
            this.parameter = entry.parameter;
            this.entryType = entry.ptype;
            String name = entryPath[0].getName().substring(entry.parameter.getQualifiedName().length());
            entryPath[0] = new PathElement(name.isEmpty() ? null : name, entryPath[0].getIndex());
        }
    }
}
