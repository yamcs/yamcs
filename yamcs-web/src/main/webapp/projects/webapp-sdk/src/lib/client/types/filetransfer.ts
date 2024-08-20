import { WebSocketCall } from '../WebSocketCall';
import { ActionInfo } from './management';

export type FileTransferDirection = 'UPLOAD' | 'DOWNLOAD';

export type FileTransferStatus = 'RUNNING' | 'PAUSED' | 'FAILED' | 'COMPLETED' | 'CANCELLING' | 'QUEUED';

export interface FileTransferService {
  instance: string;
  name: string;
  localEntities: Entity[];
  remoteEntities: Entity[];
  capabilities: FileTransferCapabilities;
  transferOptions: FileTransferOption[];
}

export interface FileTransferOption {
  name: string;
  type: 'BOOLEAN' | 'DOUBLE' | 'STRING';
  title?: string;
  description?: string;
  associatedText?: string;
  default?: string;
  values?: { value: string, verboseName?: string; }[];
  allowCustomOption?: boolean;
}

export interface FileTransferCapabilities {
  upload: boolean;
  download: boolean;
  reliability: boolean;
  remotePath: boolean;
  hasTransferType: boolean;
  pauseResume: boolean;
  fileList: boolean;
  fileListExtraColumns?: FileListExtraColumnInfo[];
  fileActions?: ActionInfo[];
}

export interface GetFileTransfersOptions {
  start?: string;
  stop?: string;
  state?: string | string[];
  direction?: FileTransferDirection;
  localEntityId?: number;
  remoteEntityId?: number;
  limit?: number;
  order?: 'asc' | 'desc';
}

export interface FileListExtraColumnInfo {
  id: string;
  label: string;
}

export interface Entity {
  name: string;
  id: number;
}

export interface Transfer {
  id: number;
  startTime?: string;
  creationTime: string;
  state: FileTransferStatus;
  bucket: string;
  objectName: string;
  remotePath: string;
  direction: FileTransferDirection;
  totalSize: number;
  sizeTransferred: number;
  failureReason?: string;
  transferType?: string;
  localEntity: Entity;
  remoteEntity: Entity;
}

export interface CreateTransferRequest {
  direction: FileTransferDirection;
  bucket: string;
  objectName: string;
  remotePath: string;
  source: string;
  destination: string;
  options: { [key: string]: boolean | number | string; };
}

export interface TransfersPage {
  transfers: Transfer[];
}

export interface ListFilesRequest {
  source: string;
  destination: string;
  remotePath: string;
  options?: { [key: string]: boolean | number | string; };
}

export interface RemoteFile {
  name: string;
  displayName: string;
  isDirectory: boolean;
  size: number;
  modified: string;
  extra?: { [key: string]: any; };
}

export interface ListFilesResponse {
  files: RemoteFile[];
  destination: string;
  remotePath: string;
  listTime: string;
  state?: string;
  progressMessage?: string;
}

export interface RunFileActionRequest {
  remoteEntity: string;
  file: string;
  action: string;
  message?: { [key: string]: any; };
}

export interface ServicesPage {
  services: FileTransferService[];
}

export interface SubscribeTransfersRequest {
  instance: string;
  serviceName: string;
  ongoingOnly: true;
}

export interface SubscribeRemoteFileListRequest {
  instance: string;
  serviceName: string;
}

export type TransferSubscription = WebSocketCall<SubscribeTransfersRequest, Transfer>;
export type RemoteFileListSubscription = WebSocketCall<SubscribeRemoteFileListRequest, ListFilesResponse>;
