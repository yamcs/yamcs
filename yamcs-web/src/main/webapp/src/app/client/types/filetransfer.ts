import { WebSocketCall } from '../WebSocketCall';

export interface FileTransferService {
  instance: string;
  name: string;
  localEntities: Entity[];
  remoteEntities: Entity[];
  capabilities: FileTransferCapabilities;
}

export interface FileTransferCapabilities {
  upload: boolean;
  download: boolean;
  reliability: boolean;
  remotePath: boolean;
  fileList: boolean;
}

export interface Entity {
  name: string;
  id: number;
}

export interface Transfer {
  id: number;
  startTime?: string;
  creationTime: string;
  state: 'RUNNING' | 'PAUSED' | 'FAILED' | 'COMPLETED' | 'CANCELLING' | 'QUEUED';
  bucket: string;
  objectName: string;
  remotePath: string;
  direction: 'UPLOAD' | 'DOWNLOAD';
  totalSize: number;
  sizeTransferred: number;
  failureReason?: string;
}

export interface UploadOptions {
  overwrite?: boolean;
  createPath?: boolean;
  reliable?: boolean;
}

export interface DownloadOptions {
  overwrite?: boolean;
  createPath?: boolean;
  reliable?: boolean;
}

export interface CreateTransferRequest {
  direction: 'UPLOAD' | 'DOWNLOAD';
  bucket: string;
  objectName: string;
  remotePath: string;
  source: string;
  destination: string;
  uploadOptions?: UploadOptions;
  downloadOptions?: DownloadOptions;
}

export interface TransfersPage {
  transfers: Transfer[];
}

export interface ListFilesRequest {
  remotePath: string;
  destination: string;
}

export interface RemoteFile {
  name: string;
  created: string;
  size: number;
}

export interface ListFilesResponse {
  files: RemoteFile[];
  destination: string;
  remotePath: string;
  listTime: string;
}

export interface ServicesPage {
  services: FileTransferService[];
}

export interface SubscribeTransfersRequest {
  instance: string;
  serviceName: string;
}

export type TransferSubscription = WebSocketCall<SubscribeTransfersRequest, Transfer>;
export type RemoteFileListSubscription = WebSocketCall<SubscribeTransfersRequest, ListFilesResponse>;
