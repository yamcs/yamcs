package org.yamcs.web.rest.mdb;

import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;

public class MissionDatabaseHelper {
    
    /**
     * Searches for a valid parameter name in the URI of the request. It is
     * assumed that the MDB for the instance was already added to the request's
     * context.
     * 
     * @pathOffset the offset at which to start the search
     */
    public static MatchResult<Parameter> matchParameterName(RestRequest req, int pathOffset) {
        XtceDb mdb = req.getFromContext(MDBRequestHandler.CTX_MDB);
        
        MatchResult<String> nsMatch = matchXtceDbNamespace(req, pathOffset, false);
        NamedObjectId id = null;
        if (nsMatch.matches()) {
            String namespace = nsMatch.getMatch();
            if (req.hasPathSegment(nsMatch.getPathOffset())) {
                String name = req.getPathSegment(nsMatch.getPathOffset());
                id = NamedObjectId.newBuilder().setNamespace(namespace).setName(name).build();
                Parameter p = findParameter(mdb, id);
                if (p != null) {
                    return new MatchResult<>(p, nsMatch.getPathOffset() + 1, id);
                }
            }
        }

        return new MatchResult<>(null, -1);
    }
    
    public static Parameter findParameter(XtceDb mdb, NamedObjectId id) {
        Parameter p = mdb.getParameter(id);
        if(p==null) {
            p = mdb.getSystemParameterDb().getSystemParameter(id, false);
        }
        return p;
    }
    
    /**
     * Searches for a valid container name in the URI of the request. It is
     * assumed that the MDB for the instance was already added to the request's
     * context.
     * 
     * @pathOffset the offset at which to start the search
     */
    public static MatchResult<SequenceContainer> matchContainerName(RestRequest req, int pathOffset) {
        XtceDb mdb = req.getFromContext(MDBRequestHandler.CTX_MDB);
        
        MatchResult<String> nsMatch = matchXtceDbNamespace(req, pathOffset, false);
        NamedObjectId id = null;
        if (nsMatch.matches()) {
            String namespace = nsMatch.getMatch();
            if (req.hasPathSegment(nsMatch.getPathOffset())) {
                String name = req.getPathSegment(nsMatch.getPathOffset());
                id = NamedObjectId.newBuilder().setNamespace(namespace).setName(name).build();
                SequenceContainer c = mdb.getSequenceContainer(id);
                if (c != null) {
                    return new MatchResult<>(c, nsMatch.getPathOffset() + 1, id);
                }
            }
        }

        return new MatchResult<>(null, -1);
    }
    
    /**
     * Searches for a valid algorithm name in the URI of the request. It is
     * assumed that the MDB for the instance was already added to the request's
     * context.
     * 
     * @pathOffset the offset at which to start the search
     */
    public static MatchResult<Algorithm> matchAlgorithmName(RestRequest req, int pathOffset) {
        XtceDb mdb = req.getFromContext(MDBRequestHandler.CTX_MDB);
        
        MatchResult<String> nsMatch = matchXtceDbNamespace(req, pathOffset, false);
        NamedObjectId id = null;
        if (nsMatch.matches()) {
            String namespace = nsMatch.getMatch();
            if (req.hasPathSegment(nsMatch.getPathOffset())) {
                String name = req.getPathSegment(nsMatch.getPathOffset());
                id = NamedObjectId.newBuilder().setNamespace(namespace).setName(name).build();
                Algorithm a = mdb.getAlgorithm(id);
                if (a != null) {
                    return new MatchResult<>(a, nsMatch.getPathOffset() + 1, id);
                }
            }
        }

        return new MatchResult<>(null, -1);
    }
    
    /**
     * Searches for a valid command name in the URI of the request. It is
     * assumed that the MDB for the instance was already added to the request's
     * context.
     * 
     * @pathOffset the offset at which to start the search
     */
    public static MatchResult<MetaCommand> matchCommandName(RestRequest req, int pathOffset) {
        XtceDb mdb = req.getFromContext(MDBRequestHandler.CTX_MDB);
        
        MatchResult<String> nsMatch = matchXtceDbNamespace(req, pathOffset, false);
        NamedObjectId id = null;
        if (nsMatch.matches()) {
            String namespace = nsMatch.getMatch();
            if (req.hasPathSegment(nsMatch.getPathOffset())) {
                String name = req.getPathSegment(nsMatch.getPathOffset());
                id = NamedObjectId.newBuilder().setNamespace(namespace).setName(name).build();
                MetaCommand c = mdb.getMetaCommand(id);
                if (c != null) {
                    return new MatchResult<>(c, nsMatch.getPathOffset() + 1, id);
                }
            }
        }

        return new MatchResult<>(null, -1);
    }
    
    /**
     * Greedily matches a namespace
     */
    public static MatchResult<String> matchXtceDbNamespace(RestRequest req, int pathOffset, boolean strict) {
        XtceDb mdb = req.getFromContext(MDBRequestHandler.CTX_MDB);
        String matchedNamespace = null;
        
        String segment = req.getPathSegment(pathOffset);
        if (mdb.containsNamespace(segment)) {
            matchedNamespace = segment;
        } else if (mdb.containsNamespace("/" + segment)) {
            matchedNamespace = "/" + segment; 
        } else if (mdb.getSystemParameterDb().containsNamespace("/" + segment)) {
            matchedNamespace = "/" + segment;
        }
        
        int beyond = pathOffset;
        if (matchedNamespace != null) {
            beyond++;
            if (matchedNamespace.startsWith("/")) {
                for (int i = pathOffset+1; i < req.getPathSegmentCount(); i++) {
                    String potential = matchedNamespace + "/" + req.getPathSegment(i);
                    if (mdb.containsNamespace(potential) || mdb.getSystemParameterDb().containsNamespace(potential)) {
                        matchedNamespace = potential;
                        beyond++;
                    }
                }
            }
        }
        
        if (matchedNamespace != null && (!strict || beyond >= req.getPathSegmentCount())) {
            return new MatchResult<>(matchedNamespace, beyond);
        } else {
            return new MatchResult<>(null, -1);
        }
    }
    
    public static class MatchResult <T> {
        private final NamedObjectId requestedId;
        private final T match;
        private final int pathOffset; // positioned after the match
        
        MatchResult(T match, int pathOffset, NamedObjectId requestedId) {
            this.match = match;
            this.pathOffset = pathOffset;
            this.requestedId = requestedId;
        }
        
        MatchResult(T match, int pathOffset) {
            this.match = match;
            this.pathOffset = pathOffset;
            requestedId = null;
        }
        
        public boolean matches() {
            return match != null;
        }
        
        public NamedObjectId getRequestedId() {
            return requestedId;
        }
        
        public T getMatch() {
            return match;
        }
        
        public int getPathOffset() {
            return pathOffset;
        }
    }
}
