import { TransferItem } from './TransferItem';

export type WatermarkObserver = () => void;

/**
 * Combines archive transfers obtained via REST
 * with realtime transfers obtained via WebSocket.
 *
 * This class does not care about whether archive data and
 * realtime data is connected. Both sets are joined and
 * sorted under all conditions.
 */
export class FileTransferBuffer {

  public dirty = false;

  private archiveTransfers: TransferItem[] = [];

  private realtimeBuffer: (TransferItem | undefined)[];
  private bufferSize = 500;
  private bufferWatermark = 400;
  private pointer = 0;
  private alreadyWarned = false;

  constructor(private watermarkObserver: WatermarkObserver) {
    this.realtimeBuffer = Array(this.bufferSize).fill(undefined);
  }

  addArchiveData(transfers: TransferItem[]) {
    this.archiveTransfers = this.archiveTransfers.concat(transfers);
    this.dirty = true;
  }

  addRealtimeTransfer(transfer: TransferItem) {
    if (this.pointer < this.bufferSize) {
      this.realtimeBuffer[this.pointer] = transfer;
      if (this.pointer >= this.bufferWatermark && this.watermarkObserver && !this.alreadyWarned) {
        this.alreadyWarned = true;
        this.watermarkObserver();
      }
      this.pointer = this.pointer + 1;
    }
    this.dirty = true;
  }

  reset() {
    this.archiveTransfers = [];
    this.realtimeBuffer.fill(undefined);
    this.pointer = 0;
    this.alreadyWarned = false;
    this.dirty = true;
  }

  snapshot(): TransferItem[] {
    const splicedTransfers = [...this.archiveTransfers];
    this.realtimeBuffer.map(transfer => {
      if (!transfer) return;

      const existingIndex = splicedTransfers.findIndex(a => a.id === transfer.id);
      if (existingIndex === -1) {
        splicedTransfers.push(transfer);
      } else {
        splicedTransfers[existingIndex] = transfer;
      }
    });

    splicedTransfers.sort((a, b) => {
      const time1 = a.creationTime || a.startTime || '';
      const time2 = b.creationTime || b.startTime || '';
      return time2.localeCompare(time1);
    });
    return splicedTransfers;
  }

  /**
   * Transfers the realtime buffer into the archive buffer, and
   * reduces its size to a set limit. The oldest transfers (based
   * on generation time) are removed first.
   */
  compact(limit: number) {
    const snapshot = this.snapshot();
    snapshot.length = Math.min(limit, snapshot.length);
    this.reset();
    this.archiveTransfers = snapshot;
    this.dirty = true;
  }
}
