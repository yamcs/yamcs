import { BehaviorSubject } from 'rxjs';
import { DataSource } from '@angular/cdk/table';
import { GetParameterValuesOptions, ParameterValue } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';
import { CollectionViewer } from '@angular/cdk/collections';

export class ParameterDataDataSource extends DataSource<ParameterValue> {

  pageSize = 100;
  offscreenRecord: ParameterValue | null;

  pvals$ = new BehaviorSubject<ParameterValue[]>([]);
  public loading$ = new BehaviorSubject<boolean>(false);

  constructor(private yamcs: YamcsService, private qualifiedName: string) {
    super();
  }

  connect(collectionViewer: CollectionViewer) {
    return this.pvals$;
  }

  isEmpty() {
    return this.pvals$.value.length === 0;
  }

  loadParameterValues(options: GetParameterValuesOptions) {
    this.loading$.next(true);
    return this.loadPage({
      ...options,
      limit: this.pageSize + 1, // One extra to detect hasMore
    }).then(pvals => {
      this.loading$.next(false);
      this.pvals$.next(pvals);
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
  private loadPage(options: GetParameterValuesOptions) {
    return this.yamcs.getInstanceClient()!.getParameterValues(this.qualifiedName, options).then(pvals => {
      if (pvals.length > this.pageSize) {
        this.offscreenRecord = pvals.splice(pvals.length - 1, 1)[0];
      } else {
        this.offscreenRecord = null;
      }
      return pvals;
    });
  }

  /**
   * Loads the next page of data starting at where the previous page was cut off.
   * This not 100% waterproof as data may have arrived with generation time between
   * the last visible data and the offscreen record. This is unlikely to cause
   * practical problems.
   */
  loadMoreData(options: GetParameterValuesOptions) {
    if (!this.offscreenRecord) {
      return;
    }
    this.loadPage({
      ...options,
      stop: this.offscreenRecord.generationTimeUTC,
      limit: this.pageSize + 1, // One extra to detect hasMore
    }).then(pvals => {
      const combinedPvals = this.pvals$.getValue().concat(pvals);
      this.pvals$.next(combinedPvals);
    });
  }

  disconnect(collectionViewer: CollectionViewer) {
    this.pvals$.complete();
    this.loading$.complete();
  }
}
