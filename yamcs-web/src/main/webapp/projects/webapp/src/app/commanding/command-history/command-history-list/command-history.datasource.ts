import { DataSource } from '@angular/cdk/table';
import { CommandHistoryEntry, CommandHistoryRecord, CommandSubscription, GetCommandHistoryOptions, Synchronizer, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { CommandHistoryBuffer } from './CommandHistoryBuffer';

export class CommandHistoryDataSource extends DataSource<CommandHistoryRecord> {

  pageSize = 100;
  continuationToken?: string;
  options: GetCommandHistoryOptions;
  blockHasMore = false;

  records$ = new BehaviorSubject<CommandHistoryRecord[]>([]);
  private buffer: CommandHistoryBuffer;

  loading$ = new BehaviorSubject<boolean>(false);
  public streaming$ = new BehaviorSubject<boolean>(false);

  namespaces$ = new BehaviorSubject<string[]>([]);

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
      var entries = page.entry || [];
      for (const entry of entries) {
        this.updateNamespaces(entry);
      }
      return entries;
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
        this.updateNamespaces(entry);
        this.buffer.addRealtimeCommand(entry);
      }
    });
  }

  private updateNamespaces(entry: CommandHistoryEntry) {
    const knownNamespaces = this.namespaces$.value;
    if (entry.aliases) {
      for (const namespace in entry.aliases) {
        if (knownNamespaces.indexOf(namespace) === -1) {
          knownNamespaces.push(namespace);
          knownNamespaces.sort();
          this.namespaces$.next([...knownNamespaces]);
        }
      }
    }
  }

  stopStreaming() {
    this.realtimeSubscription?.cancel();
    this.streaming$.next(false);
  }

  disconnect() {
    this.syncSubscription?.unsubscribe();
    this.records$.complete();
    this.loading$.complete();
    this.streaming$.complete();
    this.namespaces$.complete();
  }

  isEmpty() {
    return !this.records$.getValue().length;
  }
}
