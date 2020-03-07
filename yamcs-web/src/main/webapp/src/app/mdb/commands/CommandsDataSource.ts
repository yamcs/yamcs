import { DataSource } from '@angular/cdk/table';
import { BehaviorSubject } from 'rxjs';
import { Command, GetCommandsOptions } from '../../client';
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
    return this.yamcs.yamcsClient.getCommands(this.yamcs.getInstance().name, options).then(page => {
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
