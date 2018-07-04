package org.yamcs.web.rest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.yamcs.protobuf.Rest.DisplayFile;
import org.yamcs.protobuf.Rest.DisplayFile.DisplaySource;
import org.yamcs.protobuf.Rest.DisplayFolder;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.WebConfig;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.BucketDatabase;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties;

/**
 * provides information about available displays
 * 
 * Currently the only method supported is "list"
 * 
 * @author nm
 *
 */
public class DisplayRestHandler extends RestHandler {

    private static final String DISPLAY_BUCKET = "displays";

    @Route(path = "/api/displays/:instance", method = "GET")
    @Route(path = "/api/displays/:instance/:path*", method = "GET")
    public void listDisplays(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        DisplayFolder.Builder responseb = DisplayFolder.newBuilder();

        String prefix = "";
        if (req.hasRouteParam("path")) {
            prefix = req.getRouteParam("path");
        }

        if (prefix.contains("..")) {
            throw new BadRequestException("Illegal path name");
        }

        if (prefix.equals("")) {
            responseb.setName("/");
            responseb.setPath("/");
        } else if (prefix.contains("/")) {
            responseb.setName(prefix.substring(prefix.lastIndexOf("/") + 1));
            responseb.setPath("/" + prefix);
        } else {
            responseb.setName(prefix);
            responseb.setPath("/" + prefix);
        }

        Bucket bucket = getOrCreateDisplayBucket(req);
        List<String> prefixes = new ArrayList<>();
        try {
            int prefixLength = prefix != null ? prefix.length() + 1 : 0;
            String searchPrefix = prefix.equals("") ? null : prefix + "/";
            List<ObjectProperties> objects = bucket.listObjects(searchPrefix, op -> {
                String name = op.getName();
                int idx = name.indexOf("/", prefixLength);
                if (idx != -1) {
                    String pref = name.substring(0, idx + 1);
                    if (prefixes.isEmpty() || !prefixes.get(prefixes.size() - 1).equals(pref)) {
                        prefixes.add(pref);
                    }
                    return false;
                } else {
                    return true;
                }
            });

            for (String p : prefixes) {
                String name = p.substring(0, p.length() - 1); // Remove trailing slash
                if (name.contains("/")) {
                    name = name.substring(name.lastIndexOf("/") + 1);
                }
                responseb.addFolder(DisplayFolder.newBuilder()
                        .setName(name).setPath("/" + p.substring(0, p.length() - 1)));
            }

            for (ObjectProperties object : objects) {
                String name = object.getName();
                if (name.contains("/")) {
                    name = name.substring(name.lastIndexOf("/") + 1);
                }
                String path = "/" + prefix + (prefix.equals("") ? name : "/" + name);
                responseb.addFile(DisplayFile.newBuilder()
                        .setName(name)
                        .setPath(path)
                        .setSource(DisplaySource.BUCKET));
            }
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }

        File displayDir = null;
        for (String webRoot : WebConfig.getInstance().getWebRoots()) {
            File dir = new File(webRoot, instance + File.separator + "displays");
            if (dir.exists()) {
                displayDir = dir;
                break;
            }
        }

        if (displayDir != null) {
            displayDir = new File(displayDir, prefix);
            writeFilesFromDir(displayDir, responseb, "/" + prefix);
        }

        // Resort results coming from the bucket and the file system
        List<DisplayFolder> sortedFolders = new ArrayList<>(responseb.getFolderList());
        Collections.sort(sortedFolders, (f1, f2) -> {
            return f1.getName().compareToIgnoreCase(f2.getName());
        });
        responseb.clearFolder();
        responseb.addAllFolder(sortedFolders);
        List<DisplayFile> sortedFiles = new ArrayList<>(responseb.getFileList());
        Collections.sort(sortedFiles, (f1, f2) -> {
            return f1.getName().compareToIgnoreCase(f2.getName());
        });
        responseb.clearFile();
        responseb.addAllFile(sortedFiles);

        completeOK(req, responseb.build());
    }

    private Bucket getOrCreateDisplayBucket(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        YarchDatabaseInstance ydi = YarchDatabase.getInstance(instance);
        try {
            BucketDatabase bucketdb = ydi.getBucketDatabase();
            Bucket bucket = bucketdb.getBucket(DISPLAY_BUCKET);
            if (bucket == null) {
                try {
                    bucket = bucketdb.createBucket(DISPLAY_BUCKET);
                } catch (IOException e) {
                    throw new InternalServerErrorException("Error creating display bucket", e);
                }
            }
            return bucket;
        } catch (YarchException e) {
            throw new InternalServerErrorException("Bucket database not available", e);
        }
    }

    private void writeFilesFromDir(File parent, DisplayFolder.Builder folderb, String parentPath)
            throws BadRequestException {
        if (!parent.exists()) {
            return;
        }
        if (!parent.isDirectory()) {
            throw new BadRequestException(String.format(
                    "Supposed to list all files from '%s' but it's not a directory", parent));
        }
        if (!parentPath.endsWith("/")) {
            parentPath = parentPath + "/";
        }
        File[] children = parent.listFiles();
        Arrays.sort(children, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
        for (File child : children) {
            if (child.isDirectory() && !containsFolder(folderb, child.getName())) {
                DisplayFolder.Builder subfolderb = DisplayFolder.newBuilder();
                subfolderb.setName(child.getName());
                subfolderb.setPath(parentPath + child.getName());
                folderb.addFolder(subfolderb);
            } else if (child.isFile() && !child.isHidden()) {
                DisplayFile.Builder subfileb = DisplayFile.newBuilder();
                subfileb.setName(child.getName());
                subfileb.setPath(parentPath + child.getName());
                subfileb.setSource(DisplaySource.FILE_SYSTEM);
                folderb.addFile(subfileb);
            }
        }
    }

    private boolean containsFolder(DisplayFolder.Builder folder, String name) {
        for (DisplayFolder sub : folder.getFolderList()) {
            if (sub.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
