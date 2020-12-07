import { WebSocketCall } from '../WebSocketCall';

export interface CfdpService {
  instance: string;
  name: string;
  sources: Entity[];
  destinations: Entity[];
}

export interface Entity {
  name: string;
  id: number;
}

export interface Transfer {
  id: number;
  startTime: string;
  state: 'RUNNING' | 'PAUSED' | 'FAILED' | 'COMPLETED';
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
  services: CfdpService[];
}

export interface SubscribeTransfersRequest {
  instance: string;
  serviceName: string;
}

export type TransferSubscription = WebSocketCall<SubscribeTransfersRequest, Transfer>;
