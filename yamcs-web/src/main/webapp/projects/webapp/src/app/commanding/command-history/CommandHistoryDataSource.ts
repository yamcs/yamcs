import { DataSource } from '@angular/cdk/table';
import { CommandSubscription, GetCommandHistoryOptions, Synchronizer } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
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
  public streaming$ = new BehaviorSubject<boolean>(false);

  private realtimeSubscription: CommandSubscription;
  private syncSubscription: Subscription;

  constructor(private yamcs: YamcsService, synchronizer: Synchronizer) {
    super();
    this.syncSubscription = synchronizer.sync(() => {
      if (this.buffer.dirty && !this.loading$.getValue()) {
        this.records$.next(this.buffer.snapshot());
        this.buffer.dirty = false;
      }
    });

    this.buffer = new CommandHistoryBuffer(() => {

      // Best solution for now, alternative is to re-establish
      // the offscreenRecord after compacting.
      this.blockHasMore = true;

      this.buffer.compact(500);
    });
  }

  connect() {
    return this.records$;
  }

  loadEntries(options: GetCommandHistoryOptions) {
    this.loading$.next(true);
    return this.loadPage({
      ...options,
      limit: this.pageSize,
    }).then(entries => {
      this.buffer.reset();
      this.blockHasMore = false;
      this.buffer.addArchiveData(entries.map(entry => new CommandHistoryRecord(entry)));
    }).finally(() => {
      this.loading$.next(false);
    });
  }

  hasMore() {
    return !!this.continuationToken && !this.blockHasMore;
  }

  private async loadPage(options: GetCommandHistoryOptions) {
    return this.yamcs.yamcsClient.getCommandHistoryEntries(this.yamcs.instance!, options).then(page => {
      this.continuationToken = page.continuationToken;
      return page.entry || [];
    });
  }

  async loadMoreData(options: GetCommandHistoryOptions) {
    if (!this.continuationToken) {
      return;
    }
    return this.loadPage({
      ...options,
      next: this.continuationToken,
      limit: this.pageSize,
    }).then(entries => {
      this.buffer.addArchiveData(entries.map(entry => new CommandHistoryRecord(entry)));
    });
  }

  startStreaming() {
    this.streaming$.next(true);
    this.realtimeSubscription = this.yamcs.yamcsClient.createCommandSubscription({
      instance: this.yamcs.instance!,
      processor: this.yamcs.processor!,
    }, entry => {
      if (!this.loading$.getValue()) {
        this.buffer.addRealtimeCommand(entry);
      }
    });
  }

  stopStreaming() {
    if (this.realtimeSubscription) {
      this.realtimeSubscription.cancel();
    }
    this.streaming$.next(false);
  }

  disconnect() {
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
    this.records$.complete();
    this.loading$.complete();
    this.streaming$.complete();
  }

  isEmpty() {
    return !this.records$.getValue().length;
  }
}
