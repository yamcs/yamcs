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
    return this.yamcs.yamcsClient.getParameters(this.yamcs.instance!, options).then(page => {
      this.loading$.next(false);
      this.totalSize$.next(page.totalSize);
      this.parameters$.next(page.parameters || []);
    });
  }

  getAliasNamespaces() {
    const namespaces: string[] = [];
    for (const parameter of this.parameters$.value) {
      if (parameter.alias) {
        for (const alias of parameter.alias) {
          if (alias.namespace && namespaces.indexOf(alias.namespace) === -1) {
            namespaces.push(alias.namespace);
          }
        }
      }
    }
    return namespaces.sort();
  }

  disconnect() {
    this.parameters$.complete();
    this.totalSize$.complete();
    this.loading$.complete();
  }
}
