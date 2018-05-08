import { BehaviorSubject } from 'rxjs';
import { DataSource } from '@angular/cdk/table';
import { CommandHistoryEntry } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';
import { CollectionViewer } from '@angular/cdk/collections';

export class CommandHistoryDataSource extends DataSource<CommandHistoryEntry> {

  entries$ = new BehaviorSubject<CommandHistoryEntry[]>([]);
  loading$ = new BehaviorSubject<boolean>(false);

  constructor(private yamcs: YamcsService) {
    super();
  }

  connect(collectionViewer: CollectionViewer) {
    return this.entries$;
  }

  loadEntries(processorName: string) {
    this.loading$.next(true);
    this.yamcs.getInstanceClient()!.getCommandHistoryEntries().then(entries => {
      // console.log(entries);
      this.loading$.next(false);
      this.entries$.next(entries);
    });
  }

  disconnect(collectionViewer: CollectionViewer) {
    this.entries$.complete();
    this.loading$.complete();
  }

  isEmpty() {
    return !this.entries$.getValue().length;
  }
}
