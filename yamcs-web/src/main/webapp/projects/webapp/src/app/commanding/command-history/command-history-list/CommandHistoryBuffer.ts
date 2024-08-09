import { CommandHistoryEntry, CommandHistoryRecord } from '@yamcs/webapp-sdk';

export type WatermarkObserver = () => void;

/**
 * Combines archive cmdhist entries obtained via REST
 * with realtime entries obtained via WebSocket.
 *
 * This class does not care about whether archive samples
 * and realtime values are connected. Both sets are joined and sorted under all conditions.
 */
export class CommandHistoryBuffer {

  public dirty = false;

  private archiveRecords: CommandHistoryRecord[] = [];

  private realtimeBuffer: (CommandHistoryEntry | undefined)[];
  private bufferSize = 500;
  private bufferWatermark = 400;
  private pointer = 0;
  private alreadyWarned = false;

  constructor(private watermarkObserver: WatermarkObserver) {
    this.realtimeBuffer = Array(this.bufferSize).fill(undefined);
  }

  addArchiveData(records: CommandHistoryRecord[]) {
    this.archiveRecords = this.archiveRecords.concat(records);
    this.dirty = true;
  }

  addRealtimeCommand(entry: CommandHistoryEntry) {
    if (this.pointer < this.bufferSize) {
      this.realtimeBuffer[this.pointer] = entry;
      if (this.pointer >= this.bufferWatermark && this.watermarkObserver && !this.alreadyWarned) {
        this.alreadyWarned = true;
        this.watermarkObserver();
      }
      this.pointer = this.pointer + 1;
    }
    this.dirty = true;
  }

  reset() {
    this.archiveRecords = [];
    this.realtimeBuffer.fill(undefined);
    this.pointer = 0;
    this.alreadyWarned = false;
    this.dirty = true;
  }

  snapshot(): CommandHistoryRecord[] {
    const splicedRecords = [...this.archiveRecords];

    this.realtimeBuffer.map(entry => {
      if (!entry) return;

      const existingIndex = splicedRecords.findIndex(r => r.id == entry.id);
      if (existingIndex === -1) {
        splicedRecords.push(new CommandHistoryRecord(entry));
      } else {
        splicedRecords[existingIndex] = splicedRecords[existingIndex].mergeEntry(entry);
      }
    });

    splicedRecords.sort((r1, r2) => {
      let res = -r1.generationTime.localeCompare(r2.generationTime);
      if (res === 0) {
        res = -r1.origin.localeCompare(r2.origin);
      }
      return res !== 0 ? res : (r2.sequenceNumber - r1.sequenceNumber);
    });
    return splicedRecords;
  }

  /**
   * Transfers the realtime buffer into the archive buffer, and
   * reduces its size to a set limit. The oldest events (based
   * on generation time) are removed first.
   */
  compact(limit: number) {
    const snapshot = this.snapshot();
    snapshot.length = Math.min(limit, snapshot.length);
    this.reset();
    this.archiveRecords = snapshot;
    this.dirty = true;
  }
}
