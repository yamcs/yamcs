import { DataSource } from '@angular/cdk/table';
import { BehaviorSubject, Subscription } from 'rxjs';
import { GetPacketsOptions, Packet } from '../../client';
import { Synchronizer } from '../../core/services/Synchronizer';
import { YamcsService } from '../../core/services/YamcsService';
import { PacketBuffer } from './PacketBuffer';

export interface AnimatablePacket extends Packet {
  animate?: boolean;
}

export class PacketsDataSource extends DataSource<AnimatablePacket> {

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
        this.packets$.next(this.buffer.snapshot());
        this.buffer.dirty = false;
      }
    });

    this.buffer = new PacketBuffer();
  }

  connect() {
    return this.packets$;
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
    });
  }

  hasMore() {
    return !!this.continuationToken && !this.blockHasMore;
  }

  private loadPage(options: GetPacketsOptions) {
    return this.yamcs.yamcsClient.getPackets(this.yamcs.getInstance(), options).then(page => {
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
    });
  }

  disconnect() {
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
    this.packets$.complete();
    this.loading$.complete();
  }

  isEmpty() {
    return !this.packets$.getValue().length;
  }
}
