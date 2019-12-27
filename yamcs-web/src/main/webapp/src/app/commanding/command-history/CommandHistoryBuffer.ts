import { CommandHistoryRecord } from './CommandHistoryRecord';

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

  addArchiveData(records: CommandHistoryRecord[]) {
    this.archiveRecords = this.archiveRecords.concat(records);
    this.dirty = true;
  }

  reset() {
    this.archiveRecords = [];
    this.dirty = true;
  }

  snapshot(): CommandHistoryRecord[] {
    return this.archiveRecords;
  }
}
