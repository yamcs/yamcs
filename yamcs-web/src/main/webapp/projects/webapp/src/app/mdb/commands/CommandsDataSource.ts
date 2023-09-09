import { DataSource } from '@angular/cdk/table';
import { Command, GetCommandsOptions, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

export class CommandsDataSource extends DataSource<Command> {

  commands$ = new BehaviorSubject<Command[]>([]);
  totalSize$ = new BehaviorSubject<number>(0);
  loading$ = new BehaviorSubject<boolean>(false);

  constructor(private yamcs: YamcsService) {
    super();
  }

  connect() {
    return this.commands$;
  }

  loadCommands(options: GetCommandsOptions) {
    this.loading$.next(true);
    return this.yamcs.yamcsClient.getCommands(this.yamcs.instance!, options).then(page => {
      this.loading$.next(false);
      this.totalSize$.next(page.totalSize);
      this.commands$.next(page.commands || []);
    });
  }

  getAliasNamespaces() {
    const namespaces: string[] = [];
    for (const command of this.commands$.value) {
      if (command.alias) {
        for (const alias of command.alias) {
          if (alias.namespace && namespaces.indexOf(alias.namespace) === -1) {
            namespaces.push(alias.namespace);
          }
        }
      }
    }
    return namespaces.sort();
  }

  disconnect() {
    this.commands$.complete();
    this.totalSize$.complete();
    this.loading$.complete();
  }
}
