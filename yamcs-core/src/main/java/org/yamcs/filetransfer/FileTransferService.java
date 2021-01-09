package org.yamcs.filetransfer;

import java.io.IOException;
import java.util.List;

import org.yamcs.YamcsService;
import org.yamcs.protobuf.EntityInfo;
import org.yamcs.yarch.Bucket;

/**
 * The file transfer service defines an interface for implementing file transfers.
 * <p>
 * The service provides file transfer operations between named "entities".
 * <p>
 * The entity term is borrowed from CFDP (CCSDS File Delivery Protocol) and it
 * can mean anything for a particular implementation. For example it could mean a host in a traditional TCP/IP network.
 * <p>
 * Each file transfer is identified by a unique 64 bit identifier.
 * 
 * @author nm
 *
 */
public interface FileTransferService extends YamcsService {

    /**
     * Get the list of configured local entities. These contain the {@code source) used in the {@link
     * #startUpload(String, Bucket, String, String, String, TransferOptions)} call.
     * 
     * <p>
     * Can return an empty list if there is only one unnamed entity.
     * @return
     */
    public List<EntityInfo> getLocalEntities();

    /**
     * Get the list of configured remote entity. These contain the {@code destination} used in the
     * {@link #startUpload(String, Bucket, String, String, String, TransferOptions)} call.
     * <p>
     * Can return an empty list if there is only one unnamed remote entity.
     * 
     * @return
     */
    public List<EntityInfo> getRemoteEntities();

    /**
     * Start a file upload.
	 *
     * @param sourceEntity
     *            the source entity. Can be null if the service supports only one unnamed source entity.
     * @param bucket
     *            the bucket containing the object to be transferred.
     * @param objectName
     *            the object name to be transferred.
     * @param destinationEntity
     *            the destination entity. Can be null if the service supports only one unnamed destination entity.
     * @param destinationPath
     *            the path on the destination where the file will be uploaded. Depending on the implementation this
     *            can be the path of a directory in which case the objectName will be used as a destination file name or
     *            can be the name of a (non-existent) file which will then be used as the destination file.
     * @param options
     *            transfer options.
     * @return
     * @throws IOException
     *             if there was a problem retrieving the object from the bucket.
     * @throws InvalidRequestException
     *             thrown if the request is invalid; possible reasons:
     *             <ul>
     *             <li>object does not exist in the bucket</li>
     *             <li>the source or destination entities are not valid</li>
     *             <li>the transfer options are invalid</li>
     *             <li>other service specific error.</li>
     *             </ul>
     */
    FileTransfer startUpload(String sourceEntity, Bucket bucket, String objectName,
            String destinationEntity, String destinationPath,
            TransferOptions options) throws IOException, InvalidRequestException;

	/**
	 * Start a file download.
	 *
	 * Parameters are the same like in startUpload().
	 */
	FileTransfer startDownload(String sourceEntity, Bucket bucket, String objectName, String destinationEntity, String destinationPath, TransferOptions options) throws IOException, InvalidRequestException;

    /**
     * Get the list of ongoing or past transfers.
     * 
     * @return the list of transfers
     */
    List<FileTransfer> getTransfers();

    /**
     * Get the file transfer with the given identifier.
     * 
     * @param id
     * @return
     */
    FileTransfer getFileTransfer(long id);

    /**
     * Pause the file transfer.
     * <p>
     * If the transfer is already paused, this operation has no effect.
     * 
     * @param transfer
     *            the transfer to be paused.
     * @throws UnsupportedOperationException
     *             if the pause operation is not supported.
     */
    void pause(FileTransfer transfer);

    /**
     * Resume the file transfer.
     * <p>
     * If the transfer is not paused, this call has no effect.
     * 
     * @param transfer
     *            the transfer to be resumed.
     * @throws UnsupportedOperationException
     *             if the resume operation is not supported.
     */
    void resume(FileTransfer transfer);

    /**
     * Cancel the file transfer.
     * 
     * @param transfer
     * @throws UnsupportedOperationException
     *             if the cancel operation is not supported.
     */
    void cancel(FileTransfer transfer);

    /**
     * Register a monitor to be called each time a file transfer is started or changes state.
     * 
     * @param listener
     */
    void registerTransferMonitor(TransferMonitor listener);

    /**
     * Unregister the monitor. If the monitor was not registered, this call has no effect.
     * 
     * @param listener
     */
    void unregisterTransferMonitor(TransferMonitor listener);
}
