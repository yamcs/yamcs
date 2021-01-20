import { DataSource } from '@angular/cdk/table';
import { BehaviorSubject } from 'rxjs';
import { Command, GetCommandsOptions } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

export class ListItem {
  spaceSystem: boolean;
  name: string;
  command?: Command;
}

export class CommandsDataSource extends DataSource<ListItem> {

  items$ = new BehaviorSubject<ListItem[]>([]);
  totalSize$ = new BehaviorSubject<number>(0);
  loading$ = new BehaviorSubject<boolean>(false);

  constructor(private yamcs: YamcsService) {
    super();
  }

  connect() {
    return this.items$;
  }

  loadCommands(options: GetCommandsOptions) {
    this.loading$.next(true);
    return this.yamcs.yamcsClient.getCommands(this.yamcs.instance!, options).then(page => {
      this.loading$.next(false);
      this.totalSize$.next(page.totalSize);
      const items: ListItem[] = [];
      for (const spaceSystem of (page.spaceSystems || [])) {
        items.push({ spaceSystem: true, name: spaceSystem });
      }
      for (const command of (page.commands || [])) {
        items.push({
          spaceSystem: false,
          name: command.qualifiedName,
          command,
        });
      }
      this.items$.next(items);
    });
  }

  disconnect() {
    this.items$.complete();
    this.totalSize$.complete();
    this.loading$.complete();
  }
}
