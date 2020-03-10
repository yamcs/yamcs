import { DataSource } from '@angular/cdk/table';
import { BehaviorSubject } from 'rxjs';
import { Algorithm, GetAlgorithmsOptions } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

export class AlgorithmsDataSource extends DataSource<Algorithm> {

  algorithms$ = new BehaviorSubject<Algorithm[]>([]);
  totalSize$ = new BehaviorSubject<number>(0);
  loading$ = new BehaviorSubject<boolean>(false);

  constructor(private yamcs: YamcsService) {
    super();
  }

  connect() {
    return this.algorithms$;
  }

  loadAlgorithms(options: GetAlgorithmsOptions) {
    this.loading$.next(true);
    this.yamcs.yamcsClient.getAlgorithms(this.yamcs.getInstance(), options).then(page => {
      this.loading$.next(false);
      this.totalSize$.next(page.totalSize);
      this.algorithms$.next(page.algorithms || []);
    });
  }

  disconnect() {
    this.algorithms$.complete();
    this.totalSize$.complete();
    this.loading$.complete();
  }
}
