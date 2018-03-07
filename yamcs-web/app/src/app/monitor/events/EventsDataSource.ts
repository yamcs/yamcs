import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { DataSource } from '@angular/cdk/table';
import { Event } from '../../../yamcs-client';
import { MatPaginator } from '@angular/material';
import { YamcsService } from '../../core/services/YamcsService';
import { CollectionViewer } from '@angular/cdk/collections';
import { finalize, catchError } from 'rxjs/operators';
import { of } from 'rxjs/observable/of';

export class EventsDataSource extends DataSource<Event> {

  events$ = new BehaviorSubject<Event[]>([]);
  public loading$ = new BehaviorSubject<boolean>(false);

  constructor(private yamcs: YamcsService, paginator: MatPaginator) {
    super();
  }

  connect(collectionViewer: CollectionViewer) {
    return this.events$;
  }

  loadEvents(start: Date, stop: Date) {
    this.loading$.next(true);
    this.yamcs.getSelectedInstance().getEvents({
      start: start.toISOString(),
      stop: stop.toISOString(),
      limit: 100,
    }).pipe(
      catchError(() => of([])),
      finalize(() => this.loading$.next(false)),
    ).subscribe(events => {
      console.log('got ', events.length);
      this.events$.next(events);
    });
  }

  disconnect(collectionViewer: CollectionViewer) {
    this.events$.complete();
    this.loading$.complete();
  }
}
