import { CollectionViewer } from '@angular/cdk/collections';
import { DataSource } from '@angular/cdk/table';
import { Activity, ActivitySubscription, GetActivitiesOptions, Synchronizer, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { ActivityBuffer } from './ActivityBuffer';

export class ActivitiesDataSource extends DataSource<Activity> {

  pageSize = 100;
  continuationToken?: string;
  options: GetActivitiesOptions;
  blockHasMore = false;

  activities$ = new BehaviorSubject<Activity[]>([]);
  private buffer: ActivityBuffer;

  public loading$ = new BehaviorSubject<boolean>(false);
  public streaming$ = new BehaviorSubject<boolean>(false);

  private realtimeSubscription: ActivitySubscription;
  private syncSubscription: Subscription;

  constructor(private yamcs: YamcsService, synchronizer: Synchronizer) {
    super();
    this.syncSubscription = synchronizer.sync(() => {
      if (this.buffer.dirty && !this.loading$.getValue()) {
        this.emitActivities();
        this.buffer.dirty = false;
      }
    });

    this.buffer = new ActivityBuffer(() => {

      // Best solution for now, alternative is to re-establish
      // the offscreenRecord after compacting.
      this.blockHasMore = true;

      this.buffer.compact(500);
    });
  }

  override connect(collectionViewer: CollectionViewer): Observable<readonly Activity[]> {
    return this.activities$;
  }

  private emitActivities() {
    const activities = this.buffer.snapshot();
    this.activities$.next(activities);
  }

  loadActivities(options: GetActivitiesOptions) {
    this.loading$.next(true);
    return Promise.all([
      this.loadPage({
        ...options,
        limit: this.pageSize,
      }),
    ]).then(results => {
      const activities = results[0];

      this.loading$.next(false);
      this.buffer.reset();
      this.blockHasMore = false;
      this.buffer.addArchiveData(activities);

      // Quick emit, don't wait on sync tick
      this.emitActivities();

      return activities;
    });
  }

  hasMore() {
    return !!this.continuationToken && !this.blockHasMore;
  }

  /**
   * Fetches a page of data and keeps track of one invisible record that
   * allows to determine if there are further page(s). The next to last
   * record is used to determine the stop date of the next query because
   * the server uses the interval bounds: [start,stop)
   */
  private loadPage(options: GetActivitiesOptions) {
    this.options = options;
    return this.yamcs.yamcsClient.getActivities(this.yamcs.instance!, options).then(page => {
      this.continuationToken = page.continuationToken;
      return page.activities || [];
    });
  }

  /**
   * Loads the next page of data starting at where the previous page was cut off.
   */
  loadMoreData(options: GetActivitiesOptions) {
    if (!this.continuationToken) {
      return;
    }
    this.loadPage({
      ...options,
      next: this.continuationToken,
      limit: this.pageSize,
    }).then(activities => {
      this.buffer.addArchiveData(activities);
    });
  }

  startStreaming() {
    this.streaming$.next(true);
    this.realtimeSubscription = this.yamcs.yamcsClient.createActivitySubscription({
      instance: this.yamcs.instance!,
    }, activity => {
      if (!this.loading$.getValue() && this.matchesFilter(activity)) {
        this.buffer.addRealtimeActivity(activity);
      }
    });
  }

  private matchesFilter(activity: Activity) {
    if (this.options) {
      if (this.options.type) {
        if (activity.type !== this.options.type) {
          return false;
        }
      }
      if (this.options.status) {
        if (activity.status !== this.options.status) {
          return false;
        }
      }
      if (this.options.q) {
        if (!activity.detail || activity.detail.indexOf(this.options.q) === -1) {
          return false;
        }
      }
    }

    return true;
  }

  stopStreaming() {
    this.realtimeSubscription?.cancel();
    this.streaming$.next(false);
  }

  override disconnect(collectionViewer: CollectionViewer): void {
    this.stopStreaming();
    this.syncSubscription?.unsubscribe();
    this.activities$.complete();
    this.loading$.complete();
    this.streaming$.complete();
  }
}
