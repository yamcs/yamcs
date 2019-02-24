import { DataSource } from '@angular/cdk/table';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import { CommandHistoryRecord } from './CommandHistoryRecord';

export class CommandHistoryDataSource extends DataSource<CommandHistoryRecord> {

  records$ = new BehaviorSubject<CommandHistoryRecord[]>([]);
  loading$ = new BehaviorSubject<boolean>(false);

  constructor(private yamcs: YamcsService) {
    super();
  }

  connect() {
    return this.records$;
  }

  loadEntries(processorName: string) {
    this.loading$.next(true);
    this.yamcs.getInstanceClient()!.getCommandHistoryEntries().then(entries => {
      this.loading$.next(false);
      this.records$.next(entries.map(entry => new CommandHistoryRecord(entry)));
    });
  }

  disconnect() {
    this.records$.complete();
    this.loading$.complete();
  }

  isEmpty() {
    return !this.records$.getValue().length;
  }
}
