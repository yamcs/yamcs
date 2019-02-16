import { DataSource } from '@angular/cdk/table';
import { Container, GetContainersOptions } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';

export class ContainersDataSource extends DataSource<Container> {

  containers$ = new BehaviorSubject<Container[]>([]);
  totalSize$ = new BehaviorSubject<number>(0);
  loading$ = new BehaviorSubject<boolean>(false);

  constructor(private yamcs: YamcsService) {
    super();
  }

  connect() {
    return this.containers$;
  }

  loadContainers(options: GetContainersOptions) {
    this.loading$.next(true);
    this.yamcs.getInstanceClient()!.getContainers(options).then(page => {
      this.loading$.next(false);
      this.totalSize$.next(page.totalSize);
      this.containers$.next(page.container || []);
    });
  }

  disconnect() {
    this.containers$.complete();
    this.totalSize$.complete();
    this.loading$.complete();
  }
}
