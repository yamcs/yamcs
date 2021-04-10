package org.yamcs.xtce.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.yamcs.xtce.AggregateDataType;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.ArrayDataType;
import org.yamcs.xtce.Container;
import org.yamcs.xtce.DataType;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.PathElement;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.util.NameReference.Type;


public class ReferenceFinder {
    Consumer<String> logger;

    public ReferenceFinder(Consumer<String> logger) {
        this.logger = logger;
    }

    /**
     * find the reference nr mentioned in the space system ss by looking either in root (if absolute reference) or in
     * the parent hierarchy if relative reference
     *
     * @return null if the reference has not been found
     */
    public FoundReference findReference(SpaceSystem rootSs, NameReference nr, SpaceSystem ss) {
        String ref = nr.getReference();
        boolean absolute = false;
        SpaceSystem startSs = null;

        if (ref.startsWith("/")) {
            absolute = true;
            startSs = rootSs;
        } else if (ref.startsWith("./") || ref.startsWith("..")) {
            absolute = true;
            startSs = ss;
        }

        if (absolute) {
            return findReference(startSs, nr);
        } else {
            // go up until the root
            FoundReference rr = null;
            startSs = ss;
            while (true) {
                rr = findReference(startSs, nr);
                if ((rr != null) || (startSs == rootSs)) {
                    break;
                }
                startSs = startSs.getParent();
            }
            return rr;
        }
    }


    /**
     * looks in the SpaceSystem ss for a namedObject with the given alias. Prints a warning in case multiple references
     * are found and returns the first one.
     * 
     * 
     * @param ss
     * @param nr
     * @return
     */
    private FoundReference findAliasReference(SpaceSystem ss, NameReference nr) {

        String alias = nr.getReference();
        List<? extends NameDescription> l;
        switch (nr.getType()) {
        case PARAMETER:
            l = ss.getParameterByAlias(alias);
            break;
        case SEQUENCE_CONTAINER:
            l = ss.getSequenceContainerByAlias(alias);
            break;
        case META_COMMAND:
            l = ss.getMetaCommandByAlias(alias);
            break;
        default:
            return null;
        }

        if (l == null || l.isEmpty()) {
            return null;
        } else if (l.size() > 1) {
            logger.accept("When looking for aliases '" + nr + "' found multiple matches: " + l);
        }
        return new FoundReference(l.get(0));
    }

    /**
     * searches for aliases in the parent hierarchy
     * 
     * @param rootSs
     * @param nr
     * @param startSs
     * @return
     */
    public FoundReference findAliasReference(SpaceSystem rootSs, NameReference nr, SpaceSystem startSs) {
        // go up until the root
        FoundReference nd = null;
        SpaceSystem ss = startSs;
        while (true) {
            nd = findAliasReference(ss, nr);
            if ((nd != null) || (ss == rootSs)) {
                break;
            }
            ss = ss.getParent();
        }
        return nd;
    }

    /**
     * find reference starting at startSs and looking through the SpaceSystem path
     * 
     * @param startSs
     * @param nr
     * @return
     */
    private FoundReference findReference(SpaceSystem startSs, NameReference nr) {
        String[] path = nr.getReference().split("/");
        SpaceSystem ss = startSs;
        for (int i = 0; i < path.length - 1; i++) {
            if (".".equals(path[i]) || "".equals(path[i])) {
                continue;
            } else if ("..".equals(path[i])) {
                ss = ss.getParent();
                if (ss == null) {
                    break; // this can only happen if the root has no parent (normally it is its own parent)
                }
                continue;
            }

            if (i == path.length - 1) {
                break;
            }

            SpaceSystem ss1 = ss.getSubsystem(path[i]);

            if ((ss1 == null) && nr.getType() == Type.PARAMETER) {
                // check if it's an aggregate specified using path separator
                Parameter p = ss.getParameter(path[i]);
                if (p != null && p.getParameterType() instanceof AggregateParameterType) {

                    PathElement[] aggregateMemberPath = getAggregateMemberPath(
                            Arrays.copyOfRange(path, i + 1, path.length));
                    if (verifyPath(p.getParameterType(), aggregateMemberPath)) {
                        /*
                         * Strangely enough, references to aggregate members using dot are not valid according to XTCE.
                         * 
                         * logger.accept("Found reference to an aggregate member using path separator '/': "
                         * + nr.getReference() + ". Please use dot '.' separator instead.");
                         */

                        return new FoundReference(p, aggregateMemberPath);
                    }
                }
                break;
            }

            if (ss1 == null) {
                break;
            }
            ss = ss1;
        }
        if (ss == null) {
            return null;
        }

        String name = path[path.length - 1];
        if ("..".equals(name)) {
            return null;
        }
        switch (nr.getType()) {
        case PARAMETER:
            return getParameterReference(ss, name);
        case PARAMETER_TYPE:
            return getSimpleReference((NameDescription) ss.getParameterType(name));
        case SEQUENCE_CONTAINER:
            return getSimpleReference(ss.getSequenceContainer(name));
        case COMMAND_CONTAINER:
            Container c = ss.getCommandContainer(name);
            if (c == null) {
                c = ss.getSequenceContainer(name);
            }
            return getSimpleReference(c);
        case META_COMMAND:
            return getSimpleReference(ss.getMetaCommand(name));
        case ALGORITHM:
            return getSimpleReference(ss.getAlgorithm(name));
        case ARGUMENT_TYPE:
            return getSimpleReference((NameDescription) ss.getArgumentType(name));
        }
        // shouldn't arrive here
        return null;
    }

    private FoundReference getParameterReference(SpaceSystem ss, String name) {
        PathElement[] path = null;
        
        String pname = name;
        int idx = findSeparator(name);
        if (idx > 0) { // this is an array or aggregate element
            path = parseReference(name.substring(idx));
            pname = name.substring(0, idx);
        }

        Parameter p = ss.getParameter(pname);
        if(p==null) {
            return null;
        }
        if (path != null && !verifyPath(p.getParameterType(), path)) {
            return null;
        }
        return new FoundReference(p, path);
    }

    public static PathElement[] parseReference(String name) {
        List<PathElement> tmp = new ArrayList<>();
        String[] p = name.split("\\.");
        for (String ps : p) {
            if (!ps.isEmpty()) {
                tmp.add(PathElement.fromString(ps));
            }
        }
        return tmp.toArray(new PathElement[0]);
    }


    public static boolean verifyPath(DataType dataType, PathElement[] path) {
        DataType ptype = dataType;
        for (PathElement pe : path) {
            if (pe.getName() != null) {
                if (!(ptype instanceof AggregateDataType)) {
                    return false;
                }
                Member m = ((AggregateDataType) ptype).getMember(pe.getName());
                if (m == null) {
                    return false;
                }
                ptype = m.getType();
            }
            if (pe.getIndex() != null) {
                int[] idx = pe.getIndex();
                if (!(ptype instanceof ArrayDataType)) {
                    return false;
                }
                ArrayDataType at = (ArrayDataType) ptype;
                if (at.getNumberOfDimensions() != idx.length) {
                    return false;
                }
                ptype = at.getElementType();
            }
        }
        return true;
    }

    private static FoundReference getSimpleReference(NameDescription nd) {
        if (nd == null) {
            return null;
        } else {
            return new FoundReference(nd);
        }
    }

    private static PathElement[] getAggregateMemberPath(String[] path) {
        PathElement[] pea = new PathElement[path.length];
        for (int i = 0; i < path.length; i++) {
            pea[i] = PathElement.fromString(path[i]);
        }
        return pea;
    }

    public static class FoundReference {
        private final NameDescription nd;
        private final PathElement[] aggregateMemberPath;

        public FoundReference(NameDescription nd, PathElement[] aggregateMemberPath) {
            if (nd == null) {
                throw new NullPointerException("nd cannot be null");
            }
            this.nd = nd;
            this.aggregateMemberPath = aggregateMemberPath;
        }

        public FoundReference(NameDescription nd) {
            if (nd == null) {
                throw new NullPointerException("nd cannot be null");
            }
            this.nd = nd;
            this.aggregateMemberPath = null;
        }

        public NameDescription getNameDescription() {
            return nd;
        }

        public PathElement[] getAggregateMemberPath() {
            return aggregateMemberPath;
        }

        @Override
        public String toString() {
            return nd.getName() + (aggregateMemberPath == null ? "" : "." + Arrays.toString(aggregateMemberPath));
        }
    }

    public static int findSeparator(String s) {
        int found = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (found == -1 && ((c == '.') || (c == '['))) {
                found = i;
            } else if (c == '/') {
                found = -1;
            }
        }
        return found;
    }
}
