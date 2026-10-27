import { DataSource } from '@angular/cdk/table';
import {
  GetPacketsOptions,
  Packet,
  Synchronizer,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { PacketBuffer } from './PacketBuffer';

export class PacketsDataSource extends DataSource<Packet> {
  pageSize = 100;
  continuationToken?: string;
  options: GetPacketsOptions;
  blockHasMore = false;

  packets$ = new BehaviorSubject<Packet[]>([]);
  private buffer: PacketBuffer;
  private requestGeneration = 0;

  loading$ = new BehaviorSubject<boolean>(false);

  private syncSubscription: Subscription;

  constructor(
    private yamcs: YamcsService,
    synchronizer: Synchronizer,
  ) {
    super();
    this.syncSubscription = synchronizer.sync(() => {
      if (this.buffer.dirty && !this.loading$.getValue()) {
        this.emitPackets();
        this.buffer.dirty = false;
      }
    });

    this.buffer = new PacketBuffer();
  }

  override connect() {
    return this.packets$;
  }

  private emitPackets() {
    this.packets$.next(this.buffer.snapshot());
  }

  async loadEntries(processorName: string, options: GetPacketsOptions) {
    const generation = ++this.requestGeneration;
    this.options = { ...options };
    this.continuationToken = undefined;
    this.buffer.reset();
    this.emitPackets();
    this.loading$.next(true);
    try {
      const page = await this.loadPage({ ...options, limit: this.pageSize });
      if (generation !== this.requestGeneration) {
        return false;
      }
      this.continuationToken = page.continuationToken;
      this.blockHasMore = false;
      this.buffer.addArchiveData(page.packets || []);
      this.emitPackets();
      return true;
    } catch (err) {
      if (generation === this.requestGeneration) {
        throw err;
      }
      return false;
    } finally {
      if (generation === this.requestGeneration) {
        this.loading$.next(false);
      }
    }
  }

  clear() {
    ++this.requestGeneration;
    this.continuationToken = undefined;
    this.buffer.reset();
    this.emitPackets();
    this.loading$.next(false);
  }

  hasMore() {
    return (
      !!this.continuationToken && !this.blockHasMore && !this.loading$.value
    );
  }

  private loadPage(options: GetPacketsOptions) {
    return this.yamcs.yamcsClient.getPackets(this.yamcs.instance!, {
      ...options,
      fields: [
        // Everything except the packet binary
        'id',
        'generationTime',
        'earthReceptionTime',
        'receptionTime',
        'sequenceNumber',
        'link',
        'size',
      ],
    });
  }

  async loadMoreData() {
    if (!this.hasMore()) {
      return;
    }
    const generation = this.requestGeneration;
    this.loading$.next(true);
    try {
      const page = await this.loadPage({
        ...this.options,
        next: this.continuationToken,
        limit: this.pageSize,
      });
      if (generation !== this.requestGeneration) {
        return;
      }
      this.continuationToken = page.continuationToken;
      this.buffer.addArchiveData(page.packets || []);
      this.emitPackets();
    } catch (err) {
      if (generation === this.requestGeneration) {
        throw err;
      }
    } finally {
      if (generation === this.requestGeneration) {
        this.loading$.next(false);
      }
    }
  }

  disconnect() {
    ++this.requestGeneration;
    this.syncSubscription?.unsubscribe();
    this.packets$.complete();
    this.loading$.complete();
  }

  isEmpty() {
    return !this.packets$.getValue().length;
  }
}
