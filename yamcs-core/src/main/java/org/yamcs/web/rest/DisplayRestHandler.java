package org.yamcs.web.rest;

import java.io.File;
import java.util.Arrays;

import org.yamcs.protobuf.Rest.DisplayFile;
import org.yamcs.protobuf.Rest.DisplayFolder;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;
import org.yamcs.web.WebConfig;

/**
 * provides information about available displays
 * 
 * Currently the only method supported is "list"
 * 
 * @author nm
 *
 */
public class DisplayRestHandler extends RestHandler {

    @Route(path = "/api/displays/:instance", method = "GET")
    public void listDisplays(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        DisplayFolder.Builder responseb = DisplayFolder.newBuilder();
        responseb.setName("/");
        responseb.setPath("/");

        File displayDir = null;
        for (String webRoot : WebConfig.getInstance().getWebRoots()) {
            File dir = new File(webRoot + File.separator + instance + File.separator + "displays");
            if (dir.exists()) {
                displayDir = dir;
                break;
            }
        }
        if (displayDir != null) {
            writeFilesFromDir(displayDir, responseb, "/");
        }
        completeOK(req, responseb.build());
    }

    private void writeFilesFromDir(File parent, DisplayFolder.Builder folderb, String parentPath)
            throws BadRequestException {
        if (!parent.isDirectory()) {
            throw new BadRequestException(String.format(
                    "Supposed to list all files from '%s' but it's not a directory", parent));
        }
        File[] children = parent.listFiles();
        Arrays.sort(children, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
        for (File child : children) {
            if (child.isDirectory()) {
                DisplayFolder.Builder subfolderb = DisplayFolder.newBuilder();
                subfolderb.setName(child.getName());
                subfolderb.setPath(parentPath + child.getName() + "/");
                writeFilesFromDir(child, subfolderb, parentPath + child.getName() + "/");
                folderb.addFolder(subfolderb);
            } else if (!child.isHidden()) {
                DisplayFile.Builder subfileb = DisplayFile.newBuilder();
                subfileb.setName(child.getName());
                subfileb.setPath(parentPath + child.getName());
                folderb.addFile(subfileb);
            }
        }
    }
}
