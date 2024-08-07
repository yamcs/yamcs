import { Transfer } from '@yamcs/webapp-sdk';

export class TransferItem implements Transfer {

  constructor(public transfer: Transfer, public objectUrl: string) {
  }

  get id() {
    return this.transfer.id;
  }

  get creationTime() {
    return this.transfer.creationTime;
  }

  get startTime() {
    return this.transfer.startTime;
  }

  get direction() {
    return this.transfer.direction;
  }

  get localEntity() {
    return this.transfer.localEntity;
  }

  get remoteEntity() {
    return this.transfer.remoteEntity;
  }

  get state() {
    return this.transfer.state;
  }

  get bucket() {
    return this.transfer.bucket;
  }

  get objectName() {
    return this.transfer.objectName;
  }

  get remotePath() {
    return this.transfer.remotePath;
  }

  get totalSize() {
    return this.transfer.totalSize;
  }

  get sizeTransferred() {
    return this.transfer.sizeTransferred;
  }

  get failureReason() {
    return this.transfer.failureReason;
  }

  get transferType() {
    return this.transfer.transferType;
  }
}
