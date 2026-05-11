import { signal } from '@angular/core';
import { TimelineItem, utils } from '@yamcs/webapp-sdk';
import { ActivityItemData } from './bands/item-band/ActivityItemData';

/**
 * Wraps a backend TimelineItem object, together with some signals.
 */
export class ItemState {
  /**
   * Start of the item. Could be the planned start if it hasn't
   * started yet.
   */
  start = signal(0);

  /**
   * Duration of the item. Could be the planned duration if it hasn't
   * started yet.
   */
  duration = signal(0);

  /**
   * By how much this item has been delayed compared to the planned
   * date, meaning it is in READY state waiting for the user to
   * start it.
   */
  delay = signal(0);

  status = signal<string>('UNKNOWN');

  /**
   * Value between 0 and 1, comparing the execution duration
   * with the planned duration (if defined).
   */
  progress = signal<number | undefined>(undefined);

  constructor(
    readonly item: TimelineItem,
    now: number,
  ) {
    this.updateState(now);
  }

  get id() {
    return this.item.id;
  }

  get name() {
    return this.item.name;
  }

  get type() {
    if (this.item.type === 'EVENT') {
      return this.item.type;
    } else if (this.item.type === 'ACTIVITY') {
      if (this.item.activityDefinition) {
        return this.item.type;
      } else {
        return 'TASK';
      }
    }
  }

  get tags() {
    return this.item.tags || [];
  }

  get failureReason() {
    return this.item.failureReason;
  }

  get plannedStart() {
    return utils.toDate(this.item.start).getTime();
  }

  get plannedStop() {
    return this.plannedStart + this.plannedDuration;
  }

  get plannedDuration() {
    return utils.convertProtoDurationToMillis(this.item.duration);
  }

  get lastRun() {
    return this.item.activityRuns?.length
      ? this.item.activityRuns[this.item.activityRuns.length - 1]
      : undefined;
  }

  get execStart() {
    const lastRun = this.lastRun;
    if (lastRun && lastRun.start !== undefined) {
      return utils.toDate(lastRun.start).getTime();
    } else {
      return undefined;
    }
  }

  get execStop() {
    const lastRun = this.lastRun;
    if (lastRun && lastRun.stop !== undefined) {
      return utils.toDate(lastRun.stop).getTime();
    } else {
      return undefined;
    }
  }

  updateState(now: number) {
    const { item } = this;
    if (item.type === 'EVENT') {
      this.updateEventState(item, now);
    } else if (item.type === 'ACTIVITY') {
      this.updateActivityState(item, now);
    }
  }

  private updateEventState(item: TimelineItem, now: number) {
    const start = utils.toDate(item.start).getTime();
    const plannedDuration = this.plannedDuration;

    let progress: number | undefined = undefined;
    let status: string;
    let duration: number;
    if (start > now) {
      status = 'PLANNED';
      duration = plannedDuration;
    } else {
      const elapsed = now - start;
      progress = Math.min(Math.max(0, elapsed / plannedDuration), 1);
      status = progress === 1 ? 'SUCCEEDED' : 'IN_PROGRESS';
      duration = Math.min(elapsed, plannedDuration);
    }

    this.start.set(start);
    this.duration.set(duration);
    this.progress.set(progress);
    this.status.set(status);
    this.delay.set(0);
  }

  private updateActivityState(item: TimelineItem, now: number) {
    const helper = new ActivityItemData(item);

    let start: number;
    let duration: number;
    let delay: number = 0;
    if (helper.execRange) {
      start = helper.execRange.start;
      duration = helper.execRange.stop - helper.execRange.start;
    } else {
      start = helper.plannedRange.start;
      duration = helper.plannedRange.stop - helper.plannedRange.start;
    }

    let progress: number | undefined = undefined;
    if (item.status === 'PLANNED') {
      progress = undefined;
    } else if (item.status === 'READY') {
      progress = 0;
      delay = now - helper.plannedRange.start;
    } else if (item.status === 'WAITING_ON_DEPENDENCY') {
      progress = undefined;
    } else if (item.status === 'IN_PROGRESS') {
      const plannedDuration = utils.convertProtoDurationToMillis(item.duration);
      if (plannedDuration === 0) {
        progress = undefined;
      } else {
        const lastRun = this.lastRun;
        if (lastRun) {
          const execStart = utils.toDate(lastRun.start).getTime();
          const execStop = now;
          const elapsed = execStop - execStart;

          progress = Math.max(0, elapsed / plannedDuration);
        }
      }
    } else if (item.status === 'SUCCEEDED') {
      const plannedDuration = utils.convertProtoDurationToMillis(item.duration);

      const lastRun = this.lastRun;
      if (lastRun) {
        const execStart = utils.toDate(lastRun.start).getTime();
        const execStop = utils.toDate(lastRun.stop).getTime();
        const elapsed = execStop - execStart;

        progress = Math.max(1, elapsed / plannedDuration);
      }
    } else if (item.status === 'ABORTED') {
      progress = undefined;
    } else if (item.status === 'FAILED') {
      progress = undefined;
    } else if (item.status === 'SKIPPED') {
      progress = undefined;
    } else if (item.status === 'CANCELED') {
      progress = undefined;
    } else if (item.status === 'PAUSED') {
      progress = undefined;
    } else if (item.status === 'WAITING_FOR_INPUT') {
      progress = undefined;
    }

    this.start.set(start);
    this.duration.set(duration);
    this.status.set(item.status!);
    this.progress.set(progress);
    this.delay.set(delay);
  }
}
