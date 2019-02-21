package org.yamcs.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.web.HttpException;

/**
 * Implements the CFDP Protocol
 * 
 * @author ddw
 *
 */

public class CfdpRestHandler extends RestHandler {
    private static final Logger log = LoggerFactory.getLogger(CfdpRestHandler.class);

    @Route(path = "/api/cfdp/:instance/:bucketName/:objectName", method = "POST")
    public void CfdpUpload(RestRequest req) throws HttpException {
        // TODO
    }

    @Route(path = "/api/cfdp/:instance/:bucketName/:objectName", method = "GET")
    public void CfdpDownload(RestRequest req) throws HttpException {
        // TODO
    }

    @Route(path = "api/cfdp/list", method = "GET")
    public void CfdpList(RestRequest req) throws HttpException {
        // TODO
    }

    @Route(path = "api/cfdp/info", method = "GET")
    public void CfdpInfo(RestRequest req) throws HttpException {
        // TODO
    }

    @Route(path = "api/cfdp/cancel", method = "POST")
    public void CfdpCancel(RestRequest req) throws HttpException {
        // TODO
    }

    @Route(path = "api/cfdp/delete", method = "POST")
    public void CfdpDelete(RestRequest req) throws HttpException {
        // TODO
    }
}
