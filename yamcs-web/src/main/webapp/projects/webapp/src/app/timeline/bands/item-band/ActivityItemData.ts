import { TimeRange } from '@fqqb/timeline';
import { TimelineItem, utils } from '@yamcs/webapp-sdk';

/**
 * Custom data associated with a Timeline item of type "Activity".
 */
export class ActivityItemData {
  item: TimelineItem;

  /**
   * Original planned range
   */
  plannedRange: TimeRange;

  /**
   * An activity is ready for execution, but awaiting user start signal
   */
  delayedRange?: TimeRange;

  /**
   * An activity is ready for execution, ahead of schedule
   */
  aheadRange?: TimeRange;

  /**
   * An activity is executing
   */
  execRange?: TimeRange;

  /**
   * Same duration as planned range, but shifted to the right
   * over the duration of the delayedRange/aheadRange.
   */
  shiftedPlannedRange?: TimeRange;

  constructor(itemInfo: TimelineItem) {
    this.item = itemInfo;

    const now = new Date().getTime();
    const plannedStart = utils.toDate(itemInfo.start).getTime();
    const duration = utils.convertProtoDurationToMillis(itemInfo.duration);
    const plannedStop = duration ? plannedStart + duration : plannedStart;
    this.plannedRange = { start: plannedStart, stop: plannedStop };

    const lastExecution = itemInfo.activityRuns?.length
      ? itemInfo.activityRuns[itemInfo.activityRuns.length - 1]
      : undefined;

    if (lastExecution) {
      const execStart = utils.toDate(lastExecution.start).getTime();
      const execStop = utils.toDate(lastExecution.stop ?? now).getTime();
      this.execRange = { start: execStart, stop: execStop };

      if (execStart > plannedStart) {
        this.delayedRange = { start: plannedStart, stop: execStart };
        if (itemInfo.status === 'IN_PROGRESS') {
          this.shiftedPlannedRange = {
            start: execStart,
            stop: execStart + this.getPlannedDuration(),
          };
        }
      }
    } else {
      if (itemInfo.status === 'READY') {
        if (plannedStart <= now) {
          this.delayedRange = { start: plannedStart, stop: now };
          this.shiftedPlannedRange = {
            start: now,
            stop: now + this.getPlannedDuration(),
          };
        } else {
          this.aheadRange = { start: now, stop: plannedStart };
        }
      }
    }
  }

  showPlannedRange() {
    return (
      this.item.status !== 'SUCCEEDED' &&
      this.item.status !== 'FAILED' &&
      this.item.status !== 'ABORTED'
    );
  }

  get minDate() {
    let dt = this.showPlannedRange() ? this.plannedRange.start : +Infinity;
    if (this.shiftedPlannedRange) {
      dt = Math.min(dt, this.shiftedPlannedRange.start);
    }
    if (this.delayedRange) {
      dt = Math.min(dt, this.delayedRange.start);
    }
    if (this.aheadRange) {
      dt = Math.min(dt, this.aheadRange.start);
    }
    if (this.execRange) {
      dt = Math.min(dt, this.execRange.start);
    }
    return dt;
  }

  get maxDate() {
    let dt = this.showPlannedRange() ? this.plannedRange.stop : -Infinity;
    if (this.shiftedPlannedRange) {
      dt = Math.max(dt, this.shiftedPlannedRange.stop);
    }
    if (this.delayedRange) {
      dt = Math.max(dt, this.delayedRange.stop);
    }
    if (this.aheadRange) {
      dt = Math.max(dt, this.aheadRange.stop);
    }
    if (this.execRange) {
      dt = Math.max(dt, this.execRange.stop);
    }
    return dt;
  }

  getPlannedDuration() {
    return this.plannedRange.stop - this.plannedRange.start;
  }

  hasExecution() {
    return this.item.activityRuns && this.item.activityRuns.length > 0;
  }
}
