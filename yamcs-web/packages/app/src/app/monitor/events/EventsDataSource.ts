import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { DataSource } from '@angular/cdk/table';
import { Event, GetEventsOptions } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';
import { CollectionViewer } from '@angular/cdk/collections';

export class EventsDataSource extends DataSource<Event> {

  pageSize = 100;
  offscreenRecord: Event | null;

  events$ = new BehaviorSubject<Event[]>([]);
  public loading$ = new BehaviorSubject<boolean>(false);

  constructor(private yamcs: YamcsService) {
    super();
  }

  connect(collectionViewer: CollectionViewer) {
    return this.events$;
  }

  loadEvents(options: GetEventsOptions) {
    this.loading$.next(true);
    return this.loadPage({
      ...options,
      limit: this.pageSize + 1, // One extra to detect hasMore
    }).then(events => {
      this.loading$.next(false);
      this.events$.next(events);
    });
  }

  hasMore() {
    return this.offscreenRecord != null;
  }

  /**
   * Fetches a page of data and keeps track of one invisible record that will
   * allow to deterimine if there are further page(s) and which stop date should
   * be used for the next page (start/stop are inclusive).
   */
  private loadPage(options: GetEventsOptions) {
    return this.yamcs.getSelectedInstance().getEvents(options).then(events => {
      if (events.length > this.pageSize) {
        this.offscreenRecord = events.splice(events.length - 1, 1)[0];
      } else {
        this.offscreenRecord = null;
      }
      return events;
    });
  }

  /**
   * Loads the next page of data starting at where the previous page was cut off.
   * This not 100% waterproof as data may have arrived with generation time between
   * the last visible data and the offscreen record. This is unlikely to cause
   * practical problems.
   */
  loadMoreData(options: GetEventsOptions) {
    if (!this.offscreenRecord) {
      return;
    }
    this.loadPage({
      ...options,
      stop: this.offscreenRecord.generationTimeUTC,
      limit: this.pageSize + 1, // One extra to detect hasMore
    }).then(events => {
      const combinedEvents = this.events$.getValue().concat(events);
      this.events$.next(combinedEvents);
    });
  }

  disconnect(collectionViewer: CollectionViewer) {
    this.events$.complete();
    this.loading$.complete();
  }
}
