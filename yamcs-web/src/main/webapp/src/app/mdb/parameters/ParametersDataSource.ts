import { DataSource } from '@angular/cdk/table';
import { BehaviorSubject } from 'rxjs';
import { GetParametersOptions, Parameter } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

export class ParametersDataSource extends DataSource<Parameter> {

  parameters$ = new BehaviorSubject<Parameter[]>([]);
  totalSize$ = new BehaviorSubject<number>(0);
  loading$ = new BehaviorSubject<boolean>(false);

  constructor(private yamcs: YamcsService) {
    super();
  }

  connect() {
    return this.parameters$;
  }

  loadParameters(options: GetParametersOptions) {
    this.loading$.next(true);
    this.yamcs.yamcsClient.getParameters(this.yamcs.getInstance().name, options).then(page => {
      this.loading$.next(false);
      this.totalSize$.next(page.totalSize);
      this.parameters$.next(page.parameters || []);
    });
  }

  disconnect() {
    this.parameters$.complete();
    this.totalSize$.complete();
    this.loading$.complete();
  }
}
