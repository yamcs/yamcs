import { DataSource } from '@angular/cdk/table';
import { BehaviorSubject, Subscription } from 'rxjs';
import { GetCommandHistoryOptions } from '../../client';
import { Synchronizer } from '../../core/services/Synchronizer';
import { YamcsService } from '../../core/services/YamcsService';
import { CommandHistoryBuffer } from './CommandHistoryBuffer';
import { CommandHistoryRecord } from './CommandHistoryRecord';

export interface AnimatableCommandHistoryRecord extends CommandHistoryRecord {
  animate?: boolean;
}

export class CommandHistoryDataSource extends DataSource<AnimatableCommandHistoryRecord> {

  pageSize = 100;
  continuationToken?: string;
  options: GetCommandHistoryOptions;
  blockHasMore = false;

  records$ = new BehaviorSubject<CommandHistoryRecord[]>([]);
  private buffer: CommandHistoryBuffer;

  loading$ = new BehaviorSubject<boolean>(false);

  private syncSubscription: Subscription;

  constructor(private yamcs: YamcsService, synchronizer: Synchronizer) {
    super();
    this.syncSubscription = synchronizer.sync(() => {
      if (this.buffer.dirty && !this.loading$.getValue()) {
        this.records$.next(this.buffer.snapshot());
        this.buffer.dirty = false;
      }
    });

    this.buffer = new CommandHistoryBuffer();
  }

  connect() {
    return this.records$;
  }

  loadEntries(processorName: string, options: GetCommandHistoryOptions) {
    this.loading$.next(true);
    return this.loadPage({
      ...options,
      limit: this.pageSize,
    }).then(entries => {
      this.loading$.next(false);
      this.buffer.reset();
      this.blockHasMore = false;
      this.buffer.addArchiveData(entries.map(entry => new CommandHistoryRecord(entry)));
    });
  }

  hasMore() {
    return !!this.continuationToken && !this.blockHasMore;
  }

  private loadPage(options: GetCommandHistoryOptions) {
    return this.yamcs.yamcsClient.getCommandHistoryEntries(this.yamcs.getInstance().name, options).then(page => {
      this.continuationToken = page.continuationToken;
      return page.entry || [];
    });
  }

  loadMoreData(options: GetCommandHistoryOptions) {
    if (!this.continuationToken) {
      return;
    }
    this.loadPage({
      ...options,
      next: this.continuationToken,
      limit: this.pageSize,
    }).then(entries => {
      this.buffer.addArchiveData(entries.map(entry => new CommandHistoryRecord(entry)));
    });
  }

  disconnect() {
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
    this.records$.complete();
    this.loading$.complete();
  }

  isEmpty() {
    return !this.records$.getValue().length;
  }
}
