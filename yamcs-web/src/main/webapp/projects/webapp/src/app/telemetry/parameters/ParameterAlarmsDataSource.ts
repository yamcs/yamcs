import { CollectionViewer } from '@angular/cdk/collections';
import { DataSource } from '@angular/cdk/table';
import { Alarm, GetAlarmsOptions, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

export class ParameterAlarmsDataSource extends DataSource<Alarm> {

  pageSize = 100;
  offscreenRecord: Alarm | null;

  alarms$ = new BehaviorSubject<Alarm[]>([]);
  public loading$ = new BehaviorSubject<boolean>(false);

  constructor(private yamcs: YamcsService, private qualifiedName: string) {
    super();
  }

  connect(collectionViewer: CollectionViewer) {
    return this.alarms$;
  }

  isEmpty() {
    return this.alarms$.value.length === 0;
  }

  loadAlarms(options: GetAlarmsOptions) {
    this.loading$.next(true);
    return this.loadPage({
      ...options,
      limit: this.pageSize + 1, // One extra to detect hasMore
    }).then(alarms => {
      this.loading$.next(false);
      this.alarms$.next(alarms);
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
  private loadPage(options: GetAlarmsOptions) {
    return this.yamcs.yamcsClient.getAlarmsForParameter(this.yamcs.instance!, this.qualifiedName, options).then(alarms => {
      if (alarms.length > this.pageSize) {
        this.offscreenRecord = alarms.splice(alarms.length - 1, 1)[0];
      } else {
        this.offscreenRecord = null;
      }
      return alarms;
    });
  }

  /**
   * Loads the next page of data starting at where the previous page was cut off.
   * This not 100% waterproof as data may have arrived with generation time between
   * the last visible data and the offscreen record. This is unlikely to cause
   * practical problems.
   */
  loadMoreData(options: GetAlarmsOptions) {
    if (!this.offscreenRecord) {
      return;
    }
    this.loadPage({
      ...options,
      stop: this.offscreenRecord.triggerTime,
      limit: this.pageSize + 1, // One extra to detect hasMore
    }).then(alarms => {
      const combinedAlarms = this.alarms$.getValue().concat(alarms);
      this.alarms$.next(combinedAlarms);
    });
  }

  disconnect() {
    this.alarms$.complete();
    this.loading$.complete();
  }
}
