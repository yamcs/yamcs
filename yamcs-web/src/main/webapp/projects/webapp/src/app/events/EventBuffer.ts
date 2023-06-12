import { Event } from '@yamcs/webapp-sdk';

export type WatermarkObserver = () => void;

/**
 * Combines archive events obtained via REST
 * with realtime events obtained via WebSocket.
 *
 * This class does not care about whether archive samples
 * and realtime values are connected. Both sets are joined and sorted under all conditions.
 */
export class EventBuffer {

  public dirty = false;

  private archiveEvents: Event[] = [];

  private realtimeBuffer: (Event | undefined)[];
  private bufferSize = 500;
  private bufferWatermark = 400;
  private pointer = 0;
  private alreadyWarned = false;

  constructor(private watermarkObserver: WatermarkObserver) {
    this.realtimeBuffer = Array(this.bufferSize).fill(undefined);
  }

  addArchiveData(events: Event[]) {
    this.archiveEvents = this.archiveEvents.concat(events);
    this.dirty = true;
  }

  addRealtimeEvent(event: Event) {
    if (this.pointer < this.bufferSize) {
      this.realtimeBuffer[this.pointer] = event;
      if (this.pointer >= this.bufferWatermark && this.watermarkObserver && !this.alreadyWarned) {
        this.alreadyWarned = true;
        this.watermarkObserver();
      }
      this.pointer = this.pointer + 1;
    }
    this.dirty = true;
  }

  reset() {
    this.archiveEvents = [];
    this.realtimeBuffer.fill(undefined);
    this.pointer = 0;
    this.alreadyWarned = false;
    this.dirty = true;
  }

  snapshot(): Event[] {
    const realtimeEvents = this.realtimeBuffer.filter(s => s !== undefined) as Event[];

    const splicedEvents = this.archiveEvents
      .concat(realtimeEvents)
      .sort((e1, e2) => {
        let res = -e1.generationTime.localeCompare(e2.generationTime);
        if (res === 0) {
          res = -e1.source.localeCompare(e2.source);
        }
        return res !== 0 ? res : (e2.seqNumber - e1.seqNumber);
      });
    return splicedEvents;
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
    this.archiveEvents = snapshot;
    this.dirty = true;
  }
}
