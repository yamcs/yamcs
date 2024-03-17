import { Activity } from '@yamcs/webapp-sdk';

export type WatermarkObserver = () => void;

/**
 * Combines archive activities obtained via REST
 * with realtime activities obtained via WebSocket.
 *
 * This class does not care about whether archive samples
 * and realtime values are connected. Both sets are joined and sorted under all conditions.
 */
export class ActivityBuffer {

  public dirty = false;

  private archiveActivities: Activity[] = [];

  private realtimeBuffer: (Activity | undefined)[];
  private bufferSize = 500;
  private bufferWatermark = 400;
  private pointer = 0;
  private alreadyWarned = false;

  constructor(private watermarkObserver: WatermarkObserver) {
    this.realtimeBuffer = Array(this.bufferSize).fill(undefined);
  }

  addArchiveData(activities: Activity[]) {
    this.archiveActivities = this.archiveActivities.concat(activities);
    this.dirty = true;
  }

  addRealtimeActivity(activity: Activity) {
    if (this.pointer < this.bufferSize) {
      this.realtimeBuffer[this.pointer] = activity;
      if (this.pointer >= this.bufferWatermark && this.watermarkObserver && !this.alreadyWarned) {
        this.alreadyWarned = true;
        this.watermarkObserver();
      }
      this.pointer = this.pointer + 1;
    }
    this.dirty = true;
  }

  reset() {
    this.archiveActivities = [];
    this.realtimeBuffer.fill(undefined);
    this.pointer = 0;
    this.alreadyWarned = false;
    this.dirty = true;
  }

  snapshot(): Activity[] {
    const splicedActivities = this.archiveActivities;

    this.realtimeBuffer.map(activity => {
      if (!activity) return;

      const existingIndex = splicedActivities.findIndex(a => a.id == activity.id);
      if (existingIndex === -1) {
        splicedActivities.push(activity);
      } else {
        splicedActivities[existingIndex] = activity;
      }
    });


    splicedActivities.sort((e1, e2) => {
      let res = -e1.start.localeCompare(e2.start);
      return res !== 0 ? res : (e2.seq - e1.seq);
    });
    return splicedActivities;
  }

  /**
   * Transfers the realtime buffer into the archive buffer, and
   * reduces its size to a set limit. The oldest activities (based
   * on generation time) are removed first.
   */
  compact(limit: number) {
    const snapshot = this.snapshot();
    snapshot.length = Math.min(limit, snapshot.length);
    this.reset();
    this.archiveActivities = snapshot;
    this.dirty = true;
  }
}
