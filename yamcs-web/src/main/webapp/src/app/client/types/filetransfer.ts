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
  reliable: boolean;
}

export interface Entity {
  name: string;
  id: number;
}

export interface Transfer {
  id: number;
  startTime: string;
  state: 'RUNNING' | 'PAUSED' | 'FAILED' | 'COMPLETED' | 'CANCELLING';
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

export interface CreateTransferRequest {
  direction: 'UPLOAD' | 'DOWNLOAD';
  bucket: string;
  objectName: string;
  remotePath: string;
  source: string;
  destination: string;
  uploadOptions?: UploadOptions;
}

export interface TransfersPage {
  transfers: Transfer[];
}

export interface ServicesPage {
  services: FileTransferService[];
}

export interface SubscribeTransfersRequest {
  instance: string;
  serviceName: string;
}

export type TransferSubscription = WebSocketCall<SubscribeTransfersRequest, Transfer>;
