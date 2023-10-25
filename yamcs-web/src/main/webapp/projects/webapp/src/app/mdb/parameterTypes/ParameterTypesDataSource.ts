import { DataSource } from '@angular/cdk/table';
import { GetParameterTypesOptions, ParameterType, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

export class ParameterTypesDataSource extends DataSource<ParameterType> {

  parameterTypes$ = new BehaviorSubject<ParameterType[]>([]);
  totalSize$ = new BehaviorSubject<number>(0);
  loading$ = new BehaviorSubject<boolean>(false);

  constructor(private yamcs: YamcsService) {
    super();
  }

  connect() {
    return this.parameterTypes$;
  }

  loadParameterTypes(options: GetParameterTypesOptions) {
    this.loading$.next(true);
    return this.yamcs.yamcsClient.getParameterTypes(this.yamcs.instance!, options).then(page => {
      this.loading$.next(false);
      this.totalSize$.next(page.totalSize);
      this.parameterTypes$.next(page.parameterTypes || []);
    });
  }

  getAliasNamespaces() {
    const namespaces: string[] = [];
    for (const parameterType of this.parameterTypes$.value) {
      if (parameterType.alias) {
        for (const alias of parameterType.alias) {
          if (alias.namespace && namespaces.indexOf(alias.namespace) === -1) {
            namespaces.push(alias.namespace);
          }
        }
      }
    }
    return namespaces.sort();
  }

  disconnect() {
    this.parameterTypes$.complete();
    this.totalSize$.complete();
    this.loading$.complete();
  }
}
