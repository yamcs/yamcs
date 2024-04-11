import { DataSource } from '@angular/cdk/table';
import { Container, GetContainersOptions, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

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
    return this.yamcs.yamcsClient.getContainers(this.yamcs.instance!, options).then(page => {
      this.loading$.next(false);
      this.totalSize$.next(page.totalSize);
      this.containers$.next(page.containers || []);
    });
  }

  getAliasNamespaces() {
    const namespaces: string[] = [];
    for (const container of this.containers$.value) {
      if (container.alias) {
        for (const alias of container.alias) {
          if (alias.namespace && namespaces.indexOf(alias.namespace) === -1) {
            namespaces.push(alias.namespace);
          }
        }
      }
    }
    return namespaces.sort();
  }

  disconnect() {
    this.containers$.complete();
    this.totalSize$.complete();
    this.loading$.complete();
  }
}
