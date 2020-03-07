import { DataSource } from '@angular/cdk/table';
import { BehaviorSubject, Subscription } from 'rxjs';
import { StreamData, StreamSubscription } from '../../client';
import { Synchronizer } from '../../core/services/Synchronizer';
import { YamcsService } from '../../core/services/YamcsService';
import { StreamBuffer } from './StreamBuffer';

export interface AnimatableStreamData extends StreamData {
  animate?: boolean;
}

export class StreamDataDataSource extends DataSource<AnimatableStreamData> {

  streamData$ = new BehaviorSubject<StreamData[]>([]);

  public loading$ = new BehaviorSubject<boolean>(false);
  public streaming$ = new BehaviorSubject<boolean>(false);
  public columns$ = new BehaviorSubject<string[]>([]);

  private streamSubscription: StreamSubscription;
  private streamBuffer = new StreamBuffer();

  private syncSubscription: Subscription;

  constructor(private yamcs: YamcsService, synchronizer: Synchronizer, private stream: string) {
    super();
    this.syncSubscription = synchronizer.sync(() => {
      if (this.streamBuffer.dirty && !this.loading$.getValue()) {
        this.streamData$.next(this.streamBuffer.snapshot().reverse());
        this.streamBuffer.dirty = false;
      }
    });
  }

  connect() {
    return this.streamData$;
  }

  startStreaming() {
    this.streaming$.next(true);
    this.streamSubscription = this.yamcs.yamcsClient.createStreamSubscription({
      instance: this.yamcs.getInstance().name,
      stream: this.stream,
    }, streamData => {
      if (!this.loading$.getValue()) {
        (streamData as AnimatableStreamData).animate = true;
        this.streamBuffer.add(streamData);

        const columns = this.columns$.value;
        for (const newColumn of streamData.column) {
          if (columns.indexOf(newColumn.name) === -1) {
            columns.push(newColumn.name);
          }
        }
        this.columns$.next([...columns]);
      }
    });
  }

  stopStreaming() {
    if (this.streamSubscription) {
      this.streamSubscription.cancel();
    }
    this.streaming$.next(false);
  }

  disconnect() {
    if (this.streamSubscription) {
      this.streamSubscription.cancel();
    }
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
    this.streamData$.complete();
    this.loading$.complete();
    this.streaming$.complete();
  }
}
