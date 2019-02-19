import { DataSource } from '@angular/cdk/table';
import { Algorithm, GetAlgorithmsOptions } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
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
    this.yamcs.getInstanceClient()!.getAlgorithms(options).then(page => {
      this.loading$.next(false);
      this.totalSize$.next(page.totalSize);
      this.algorithms$.next(page.algorithm || []);
    });
  }

  disconnect() {
    this.algorithms$.complete();
    this.totalSize$.complete();
    this.loading$.complete();
  }
}
