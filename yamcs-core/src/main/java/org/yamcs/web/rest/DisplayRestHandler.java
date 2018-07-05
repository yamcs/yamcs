package org.yamcs.web.rest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.yamcs.protobuf.Rest.DisplayFile;
import org.yamcs.protobuf.Rest.DisplayFile.DisplaySource;
import org.yamcs.protobuf.Rest.DisplayFolder;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.NotFoundException;
import org.yamcs.web.WebConfig;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.BucketDatabase;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Provides information about available displays.
 * 
 * Currently this API covers both file system displays and displays stored in a bucket. As soon as we have an upload
 * component on the web interface, the file system displays will be phased out, and so will this API in favour of the
 * bucket API.
 * 
 * @author nm
 */
public class DisplayRestHandler extends RestHandler {

    private static final String DISPLAY_BUCKET = "displays";

    /**
     * Creates or updates a display resource. If the file is found on the file system, then it is overwritten. Otherwise
     * it gets saved to the displays bucket.
     * <p>
     * This is a very simple temporary api which does not work with multipart but instead expects the display text in
     * the request body.
     */
    @Route(path = "/api/displays/:instance", method = "POST")
    @Route(path = "/api/displays/:instance/:path*", method = "POST")
    public void uploadDisplay(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));

        checkObjectPrivileges(req, ObjectPrivilegeType.ManageBucket, DISPLAY_BUCKET);

        String prefix = "";
        if (req.hasRouteParam("path")) {
            prefix = req.getRouteParam("path");
        }

        if (prefix.contains("..")) {
            throw new BadRequestException("Illegal path name");
        }

        ByteBuf buf = req.bodyAsBuf();
        byte[] raw = new byte[buf.readableBytes()];
        req.bodyAsBuf().readBytes(raw);

        // Save to file system
        boolean saved = false;
        File displayDir = locateDisplayRoot(instance);
        if (displayDir != null) {
            File updatable = new File(displayDir, prefix);
            if (updatable.exists() && !updatable.isHidden() && updatable.isFile()) {
                try (FileOutputStream fileOut = new FileOutputStream(updatable)) {
                    fileOut.write(raw);
                } catch (IOException e) {
                    throw new InternalServerErrorException(e);
                }
                saved = true;
            }
        }

        // Or - save to bucket
        if (!saved) {
            Bucket bucket = getOrCreateDisplayBucket(req);
            try {
                bucket.putObject(prefix, "application/octet-stream", Collections.emptyMap(), raw);
            } catch (IOException e) {
                throw new InternalServerErrorException(e);
            }
        }

        completeOK(req);
    }

    @Route(path = "/api/displays/:instance/:path*", method = "GET")
    public void getDisplay(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));

        String prefix = req.getRouteParam("path");
        if (prefix.contains("..")) {
            throw new BadRequestException("Illegal path name");
        }

        File displayDir = locateDisplayRoot(instance);
        if (displayDir != null) {
            File displayFile = new File(displayDir, prefix);
            if (displayFile.exists() && !displayFile.isHidden() && displayFile.isFile()) {
                try {
                    byte[] raw = Files.readAllBytes(displayFile.toPath());
                    String contentType = "application/octet-stream";
                    completeOK(req, contentType, Unpooled.wrappedBuffer(raw));
                    return;
                } catch (IOException e) {
                    throw new InternalServerErrorException(e);
                }
            }
        }

        Bucket bucket = getOrCreateDisplayBucket(req);
        try {
            ObjectProperties props = bucket.findObject(prefix);
            if (props == null) {
                throw new NotFoundException(req);
            }
            byte[] raw = bucket.getObject(prefix);
            String contentType = props.hasContentType() ? props.getContentType() : "application/octet-stream";
            completeOK(req, contentType, Unpooled.wrappedBuffer(raw));
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }
    }

    @Route(path = "/api/displays/:instance/:path*", method = "DELETE")
    public void deleteDisplays(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));

        checkObjectPrivileges(req, ObjectPrivilegeType.ManageBucket, DISPLAY_BUCKET);

        String prefix = req.getRouteParam("path");
        if (prefix.contains("..")) {
            throw new BadRequestException("Illegal path name");
        }

        // Delete from display dir
        File displayDir = locateDisplayRoot(instance);
        if (displayDir != null) {
            File deletable = new File(displayDir, prefix);
            if (deletable.exists() && !deletable.isHidden()) {
                if (deletable.isFile()) {
                    deletable.delete();
                } else if (deletable.isDirectory()) {
                    deleteFolder(deletable);
                }
            }
        }

        // Delete from bucket
        Bucket bucket = getOrCreateDisplayBucket(req);
        try {
            for (ObjectProperties object : bucket.listObjects(prefix)) {
                bucket.deleteObject(object.getName());
            }
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }
        completeOK(req);
    }

    private static void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        for (File f : files) {
            if (f.isDirectory()) {
                deleteFolder(f);
            } else {
                f.delete();
            }
        }
        folder.delete();
    }

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

        File displayDir = locateDisplayRoot(instance);
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

    private File locateDisplayRoot(String instance) {
        for (String webRoot : WebConfig.getInstance().getWebRoots()) {
            File dir = new File(webRoot, instance + File.separator + "displays");
            if (dir.exists()) {
                return dir;
            }
        }
        return null;
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
