export interface Transfer {
  transactionId: number;
  startTime: string;
  state: 'RUNNING' | 'PAUSED' | 'FAILED' | 'COMPLETED';
  bucket: string;
  objectName: string;
  remotePath: string;
  direction: 'UPLOAD' | 'DOWNLOAD';
  totalSize: number;
  sizeTransferred: number;
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
  uploadOptions?: UploadOptions;
}

export interface EditTransferRequest {
  operation: string;
}

export interface TransfersPage {
  transfer: Transfer[];
}
