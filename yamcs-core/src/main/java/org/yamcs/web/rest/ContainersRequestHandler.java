package org.yamcs.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Mdb.ContainerInfo;
import org.yamcs.protobuf.Rest.ListContainersResponse;
import org.yamcs.protobuf.SchemaMdb;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.web.rest.XtceToGpbAssembler.DetailLevel;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;

/**
 * Handles incoming requests related to containers
 * <p>
 * /api/:instance/containers
 */
public class ContainersRequestHandler extends RestRequestHandler {
    final static Logger log = LoggerFactory.getLogger(ContainersRequestHandler.class.getName());
    
    @Override
    public String getPath() {
        return "containers";
    }
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        XtceDb mdb = loadMdb(req.getYamcsInstance());
        if (!req.hasPathSegment(pathOffset)) {
            return listContainers(req, null, mdb);
        } else {
            // Find out if it's a container or not. Support any namespace here. Not just XTCE
            if (req.getPathSegmentCount() - pathOffset < 2) {
                String lastSegment = req.slicePath(-1);
                NamedObjectId id = NamedObjectId.newBuilder().setName(lastSegment).build();
                SequenceContainer c = mdb.getSequenceContainer(id);
                if (c != null) { // Possibly a URL-encoded qualified name
                    return getSingleContainer(req, id, c);
                } else { // Assume it's a namespace
                    return listContainers(req, lastSegment, mdb);
                }
            } else {
                String namespace = req.slicePath(pathOffset, -1);
                String rootedNamespace = "/" + namespace;
                String lastSegment = req.slicePath(-1);
                NamedObjectId id = NamedObjectId.newBuilder().setNamespace(namespace).setName(lastSegment).build();
                SequenceContainer c = mdb.getSequenceContainer(id);
                if (c != null)
                    return getSingleContainer(req, id, c);
                
                id = NamedObjectId.newBuilder().setNamespace(rootedNamespace).setName(lastSegment).build();
                c = mdb.getSequenceContainer(id);
                if (c != null)
                    return getSingleContainer(req, id, c);
                
                // Assume it's a namespace
                return listContainers(req, namespace + "/" + lastSegment, mdb);
            }
        }
    }
    
    private RestResponse getSingleContainer(RestRequest req, NamedObjectId id, SequenceContainer c) throws RestException {
        // TODO privileges
        ContainerInfo cinfo = XtceToGpbAssembler.toContainerInfo(c, req.getInstanceURL(), DetailLevel.FULL);
        return new RestResponse(req, cinfo, SchemaMdb.ContainerInfo.WRITE);
    }

    /**
     * Sends the commands for the requested yamcs instance. If no namespace
     * is specified, assumes root namespace.
     */
    private RestResponse listContainers(RestRequest req, String namespace, XtceDb mdb) throws RestException {
        NameDescriptionSearchMatcher matcher = null;
        if (req.hasQueryParameter("q")) {
            matcher = new NameDescriptionSearchMatcher(req.getQueryParameter("q"));    
        }
        
        ListContainersResponse.Builder responseb = ListContainersResponse.newBuilder();
        if (namespace == null) {
            for (SequenceContainer c : mdb.getSequenceContainers()) {
                if (matcher != null && !matcher.matches(c)) continue;
                responseb.addContainer(XtceToGpbAssembler.toContainerInfo(c, req.getInstanceURL(), DetailLevel.SUMMARY));
            }
        } else {
            // TODO privileges
            String rootedNamespace = "/" + namespace;
            for (SequenceContainer c : mdb.getSequenceContainers()) {
                if (matcher != null && !matcher.matches(c))
                    continue;
                
                String alias = c.getAlias(namespace);
                if (alias != null) {
                    responseb.addContainer(XtceToGpbAssembler.toContainerInfo(c, req.getInstanceURL(), DetailLevel.SUMMARY));
                } else {
                    // Slash is not added to the URL so it makes it a bit more difficult
                    // to test for both XTCE names and other names. So just test with slash too
                    alias = c.getAlias(rootedNamespace);
                    if (alias != null) {
                        responseb.addContainer(XtceToGpbAssembler.toContainerInfo(c, req.getInstanceURL(), DetailLevel.SUMMARY));
                    }
                }
            }
        }
        
        // There's no such thing as a list of 'namespaces' within the MDB, therefore it
        // could happen that we arrive here but that the user intended to search for a single
        // parameter rather than a list. So... return a 404 if we didn't find any match.
        if (matcher == null && (responseb.getContainerList() == null || responseb.getContainerList().isEmpty())) {
            throw new NotFoundException(req);
        } else {
            return new RestResponse(req, responseb.build(), SchemaRest.ListContainersResponse.WRITE);
        }
    }
}
