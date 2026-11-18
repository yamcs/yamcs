import { DataSource } from '@angular/cdk/table';
import {
  CommandHistoryEntry,
  CommandHistoryRecord,
  CommandSubscription,
  GetCommandHistoryOptions,
  Synchronizer,
  YamcsService,
} from '@yamcs/webapp-sdk';
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
  extraNames$ = new BehaviorSubject<string[]>([]);

  private realtimeSubscription: CommandSubscription;
  private syncSubscription: Subscription;

  constructor(
    private yamcs: YamcsService,
    synchronizer: Synchronizer,
  ) {
    super();
    this.syncSubscription = synchronizer.sync(() => {
      if (this.buffer.dirty && !this.loading$.getValue()) {
        this.emitCommands();
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

  private emitCommands() {
    this.records$.next(this.buffer.snapshot());
  }

  loadEntries(options: GetCommandHistoryOptions) {
    this.loading$.next(true);
    return this.loadPage({
      ...options,
      limit: this.pageSize,
    })
      .then((entries) => {
        this.buffer.reset();
        this.blockHasMore = false;
        const records = entries.map((entry) => new CommandHistoryRecord(entry));
        this.updateExtraNames(records);
        this.buffer.addArchiveData(records);

        // Quick emit, don't wait on sync tick
        this.emitCommands();
      })
      .finally(() => {
        this.loading$.next(false);
      });
  }

  hasMore() {
    return !!this.continuationToken && !this.blockHasMore;
  }

  private async loadPage(options: GetCommandHistoryOptions) {
    return this.yamcs.yamcsClient
      .getCommandHistoryEntries(this.yamcs.instance!, options)
      .then((page) => {
        this.continuationToken = page.continuationToken;
        var commands = page.commands || [];
        for (const command of commands) {
          this.updateNamespaces(command);
        }
        return commands;
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
    }).then((entries) => {
      const records = entries.map((entry) => new CommandHistoryRecord(entry));
      this.updateExtraNames(records);
      this.buffer.addArchiveData(records);

      // Quick emit, don't wait on sync tick
      this.emitCommands();
    });
  }

  startStreaming() {
    this.streaming$.next(true);
    this.realtimeSubscription =
      this.yamcs.yamcsClient.createCommandSubscription(
        {
          instance: this.yamcs.instance!,
          processor: this.yamcs.processor!,
        },
        (entry) => {
          if (!this.loading$.getValue()) {
            this.updateNamespaces(entry);
            this.updateExtraNames([new CommandHistoryRecord(entry)]);
            this.buffer.addRealtimeCommand(entry);
          }
        },
      );
  }

  private updateNamespaces(command: CommandHistoryEntry) {
    const knownNamespaces = this.namespaces$.value;
    if (command.aliases) {
      for (const namespace in command.aliases) {
        if (knownNamespaces.indexOf(namespace) === -1) {
          knownNamespaces.push(namespace);
          knownNamespaces.sort();
          this.namespaces$.next([...knownNamespaces]);
        }
      }
    }
  }

  private updateExtraNames(records: CommandHistoryRecord[]) {
    const knownNames = this.extraNames$.value;
    let changed = false;
    for (const record of records) {
      for (const extra of record.extra) {
        if (knownNames.indexOf(extra.name) === -1) {
          knownNames.push(extra.name);
          changed = true;
        }
      }
    }
    if (changed) {
      knownNames.sort();
      this.extraNames$.next([...knownNames]);
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
    this.extraNames$.complete();
  }

  isEmpty() {
    return !this.records$.getValue().length;
  }
}
