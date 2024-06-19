import { DataSource } from '@angular/cdk/table';
import { GetPacketsOptions, Packet, Synchronizer, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { PacketBuffer } from './PacketBuffer';

export class PacketsDataSource extends DataSource<Packet> {

  pageSize = 100;
  continuationToken?: string;
  options: GetPacketsOptions;
  blockHasMore = false;

  packets$ = new BehaviorSubject<Packet[]>([]);
  private buffer: PacketBuffer;

  loading$ = new BehaviorSubject<boolean>(false);

  private syncSubscription: Subscription;

  constructor(private yamcs: YamcsService, synchronizer: Synchronizer) {
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

  loadEntries(processorName: string, options: GetPacketsOptions) {
    this.loading$.next(true);
    return this.loadPage({
      ...options,
      limit: this.pageSize,
    }).then(packets => {
      this.loading$.next(false);
      this.buffer.reset();
      this.blockHasMore = false;
      this.buffer.addArchiveData(packets);

      // Quick emit, don't wait on sync tick
      this.emitPackets();
    });
  }

  hasMore() {
    return !!this.continuationToken && !this.blockHasMore;
  }

  private loadPage(options: GetPacketsOptions) {
    return this.yamcs.yamcsClient.getPackets(this.yamcs.instance!, {
      ...options,
      fields: [ // Everything except the packet binary
        'id',
        'generationTime',
        'earthReceptionTime',
        'receptionTime',
        'sequenceNumber',
        'link',
        'size',
      ],
    }).then(page => {
      this.continuationToken = page.continuationToken;
      return page.packet || [];
    });
  }

  loadMoreData(options: GetPacketsOptions) {
    if (!this.continuationToken) {
      return;
    }
    this.loadPage({
      ...options,
      next: this.continuationToken,
      limit: this.pageSize,
    }).then(packets => {
      this.buffer.addArchiveData(packets);

      // Quick emit, don't wait on sync tick
      this.emitPackets();
    });
  }

  disconnect() {
    this.syncSubscription?.unsubscribe();
    this.packets$.complete();
    this.loading$.complete();
  }

  isEmpty() {
    return !this.packets$.getValue().length;
  }
}
