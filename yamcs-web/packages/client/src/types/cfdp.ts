export interface Transfer {
  transferId: number;
  state: 'RUNNING' | 'PAUSED' | 'FAILED' | 'COMPLETED';
  localBucketName: string;
  localObjectName: string;
  remotePath: string;
  direction: 'UPLOAD' | 'DOWNLOAD';
  totalSize: number;
  sizeTransferred: number;
}

export interface TransfersPage {
  transfers: Transfer[];
}
