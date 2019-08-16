import { DataSource } from '@angular/cdk/table';
import { Command, GetCommandsOptions } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';

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
    this.yamcs.getInstanceClient()!.getCommands(options).then(page => {
      this.loading$.next(false);
      this.totalSize$.next(page.totalSize);
      this.commands$.next(page.commands || []);
    });
  }

  disconnect() {
    this.commands$.complete();
    this.totalSize$.complete();
    this.loading$.complete();
  }
}
