package org.yamcs.web.rest;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ErrorInCommand;
import org.yamcs.NoPermissionException;
import org.yamcs.YProcessor;
import org.yamcs.YamcsException;
import org.yamcs.commanding.CommandQueue;
import org.yamcs.commanding.CommandQueueManager;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Mdb.CommandInfo;
import org.yamcs.protobuf.Rest.IssueCommandRequest;
import org.yamcs.protobuf.Rest.IssueCommandRequest.Assignment;
import org.yamcs.protobuf.Rest.IssueCommandResponse;
import org.yamcs.protobuf.Rest.ListCommandsResponse;
import org.yamcs.protobuf.SchemaMdb;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.Privilege;
import org.yamcs.security.Privilege.Type;
import org.yamcs.utils.StringConvertors;
import org.yamcs.web.rest.XtceToGpbAssembler.DetailLevel;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;

import com.google.protobuf.ByteString;

/**
 * Handles incoming requests related to commands
 */
public class CommandsRequestHandler extends RestRequestHandler {
    final static Logger log = LoggerFactory.getLogger(CommandsRequestHandler.class.getName());
    
    @Override
    public String getPath() {
        return "commands";
    }
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        XtceDb mdb = loadMdb(req.getYamcsInstance());
        if (!req.hasPathSegment(pathOffset)) {
            return listCommands(req, null, mdb);
        } else {
            // Find out if it's a command or not. Support any namespace here. Not just XTCE
            if (req.getPathSegmentCount() - pathOffset < 2) {
                String lastSegment = req.slicePath(-1);
                NamedObjectId id = NamedObjectId.newBuilder().setName(lastSegment).build();
                MetaCommand cmd = mdb.getMetaCommand(id);
                if (cmd != null) { // Possibly a URL-encoded qualified name
                    if (req.isGET()) {
                        return getSingleCommand(req, id, cmd);
                    } else if (req.isPOST()) {
                        return issueCommand(req, id, cmd);
                    } else {
                        throw new MethodNotAllowedException(req);
                    }
                } else { // Assume it's a namespace
                    return listCommands(req, lastSegment, mdb);
                }
            } else {
                String namespace = req.slicePath(pathOffset, -1);
                String rootedNamespace = "/" + namespace;
                String lastSegment = req.slicePath(-1);
                NamedObjectId id = NamedObjectId.newBuilder().setNamespace(namespace).setName(lastSegment).build();
                MetaCommand cmd = mdb.getMetaCommand(id);
                if (cmd != null) {
                    if (req.isGET()) {
                        return getSingleCommand(req, id, cmd);
                    } else if (req.isPOST()) {
                        return issueCommand(req, id, cmd);
                    } else {
                        throw new MethodNotAllowedException(req);
                    }
                }
                
                id = NamedObjectId.newBuilder().setNamespace(rootedNamespace).setName(lastSegment).build();
                cmd = mdb.getMetaCommand(id);
                if (cmd != null) {
                    if (req.isGET()) {
                        return getSingleCommand(req, id, cmd);
                    } else if (req.isPOST()) {
                        return issueCommand(req, id, cmd);
                    } else {
                        throw new MethodNotAllowedException(req);
                    }
                }
                
                // Assume it's a namespace
                return listCommands(req, namespace + "/" + lastSegment, mdb);
            }
        }
    }
    
    private RestResponse getSingleCommand(RestRequest req, NamedObjectId id, MetaCommand cmd) throws RestException {
        if (!Privilege.getInstance().hasPrivilege(req.authToken, Privilege.Type.TC, cmd.getQualifiedName())) {
            log.warn("Command Info for {} not authorized for token {}, throwing BadRequestException", id, req.authToken);
            throw new BadRequestException("Invalid command name specified "+id);
        }
        CommandInfo cinfo = XtceToGpbAssembler.toCommandInfo(cmd, req.getInstanceURL(), DetailLevel.FULL);
        return new RestResponse(req, cinfo, SchemaMdb.CommandInfo.WRITE);
    }

    /**
     * Sends the commands for the requested yamcs instance. If no namespace
     * is specified, assumes root namespace.
     */
    private RestResponse listCommands(RestRequest req, String namespace, XtceDb mdb) throws RestException {
        NameDescriptionSearchMatcher matcher = null;
        if (req.hasQueryParameter("q")) {
            matcher = new NameDescriptionSearchMatcher(req.getQueryParameter("q"));    
        }
        
        ListCommandsResponse.Builder responseb = ListCommandsResponse.newBuilder();
        if (namespace == null) {
            for (MetaCommand cmd : mdb.getMetaCommands()) {
                if (matcher != null && !matcher.matches(cmd)) continue;
                responseb.addCommand(XtceToGpbAssembler.toCommandInfo(cmd, req.getInstanceURL(), DetailLevel.SUMMARY));
            }
        } else {
            String rootedNamespace = "/" + namespace;
            Privilege privilege = Privilege.getInstance();
            for (MetaCommand cmd : mdb.getMetaCommands()) {
                if (!privilege.hasPrivilege(req.authToken, Type.TC, cmd.getQualifiedName()))
                    continue;
                if (matcher != null && !matcher.matches(cmd))
                    continue;
                
                String alias = cmd.getAlias(namespace);
                if (alias != null) {
                    responseb.addCommand(XtceToGpbAssembler.toCommandInfo(cmd, req.getInstanceURL(), DetailLevel.SUMMARY));
                } else {
                    // Slash is not added to the URL so it makes it a bit more difficult
                    // to test for both XTCE names and other names. So just test with slash too
                    alias = cmd.getAlias(rootedNamespace);
                    if (alias != null) {
                        responseb.addCommand(XtceToGpbAssembler.toCommandInfo(cmd, req.getInstanceURL(), DetailLevel.SUMMARY));
                    }
                }
            }
        }
        
        // There's no such thing as a list of 'namespaces' within the MDB, therefore it
        // could happen that we arrive here but that the user intended to search for a single
        // parameter rather than a list. So... return a 404 if we didn't find any match.
        if (matcher == null && (responseb.getCommandList() == null || responseb.getCommandList().isEmpty())) {
            throw new NotFoundException(req);
        } else {
            return new RestResponse(req, responseb.build(), SchemaRest.ListCommandsResponse.WRITE);
        }
    }
    
    private RestResponse issueCommand(RestRequest req, NamedObjectId id, MetaCommand cmd) throws RestException {
        if (!Privilege.getInstance().hasPrivilege(req.authToken, Privilege.Type.TC, cmd.getQualifiedName())) {
            log.warn("Command Info for {} not authorized for token {}, throwing BadRequestException", id, req.authToken);
            throw new BadRequestException("Invalid command name specified "+id);
        }
        
        YProcessor processor = YProcessor.getInstance(req.getYamcsInstance(), "realtime");
        if (!processor.hasCommanding()) {
            throw new BadRequestException("Commanding not activated for this processor");
        }

        String origin = "";
        int sequenceNumber = 0;
        boolean dryRun = false;
        List<ArgumentAssignment> assignments = new ArrayList<>();
        if (req.hasBody()) {
            IssueCommandRequest request = req.bodyAsMessage(SchemaRest.IssueCommandRequest.MERGE).build();
            if (request.hasOrigin()) origin = request.getOrigin();
            if (request.hasDryRun()) dryRun = request.getDryRun();
            if (request.hasSequenceNumber()) sequenceNumber = request.getSequenceNumber();
            for (Assignment a : request.getAssignmentList()) {
                assignments.add(new ArgumentAssignment(a.getName(), a.getValue()));
            }
        }
        
        // Override with params from query string
        for (String p : req.getQueryParameters().keySet()) {
            switch (p) {
            case "origin":
                origin = req.getQueryParameter("origin");
                break;
            case "sequenceNumber":
                sequenceNumber = req.getQueryParameterAsInt("sequenceNumber");
                break;
            case "dryRun":
                dryRun = req.getQueryParameterAsBoolean("dryRun");
                break;
            case "pretty":
                break;
            default:
                String value = req.getQueryParameter(p);
                assignments.add(new ArgumentAssignment(p, value));
            }
        }
        
        // Prepare the command
        PreparedCommand preparedCommand;
        try {
            preparedCommand = processor.getCommandingManager().buildCommand(cmd, assignments, origin, sequenceNumber, req.authToken);
            
            //make the source - should perhaps come from the client
            StringBuilder sb = new StringBuilder();
            sb.append(id.getName());
            sb.append("(");
            boolean first = true;
            for(ArgumentAssignment aa:assignments) {
                if(!first) {
                    sb.append(", ");
                } else {
                    first = false;
                }
                sb.append(aa.getArgumentName()).append(": ").append(aa.getArgumentValue());
            }
            sb.append(")");
            preparedCommand.setSource(sb.toString());
        } catch (NoPermissionException e) {
            throw new ForbiddenException(e);
        } catch (ErrorInCommand e) {
            throw new BadRequestException(e);
        } catch (YamcsException e) { // could be anything, consider as internal server error
            throw new InternalServerErrorException(e);
        }
        
        // Good, now send
        CommandQueue queue;
        if (dryRun) {
            CommandQueueManager mgr = processor.getCommandingManager().getCommandQueueManager();
            queue = mgr.getQueue(req.authToken, preparedCommand);
        } else {
            queue = processor.getCommandingManager().sendCommand(req.authToken, preparedCommand);
        }
        
        IssueCommandResponse.Builder response = IssueCommandResponse.newBuilder();
        response.setQueue(queue.getName());
        response.setSource(preparedCommand.getSource());
        response.setBinary(ByteString.copyFrom(preparedCommand.getBinary()));
        response.setHex(StringConvertors.arrayToHexString(preparedCommand.getBinary()));
        return new RestResponse(req, response.build(), SchemaRest.IssueCommandResponse.WRITE);
    }
}
