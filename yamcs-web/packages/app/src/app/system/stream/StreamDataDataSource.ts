import { CollectionViewer } from '@angular/cdk/collections';
import { DataSource } from '@angular/cdk/table';
import { StreamData } from '@yamcs/client';
import { BehaviorSubject, Subscription } from 'rxjs';
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

  private streamSubscription: Subscription;
  private streamBuffer = new StreamBuffer();

  private synchronizer: number;

  constructor(private yamcs: YamcsService, private stream: string) {
    super();
    this.synchronizer = window.setInterval(() => {
      if (this.streamBuffer.dirty && !this.loading$.getValue()) {
        this.streamData$.next(this.streamBuffer.snapshot().reverse());
        this.streamBuffer.dirty = false;
      }
    }, 500 /* update rate */);
  }

  connect(collectionViewer: CollectionViewer) {
    return this.streamData$;
  }

  startStreaming() {
    this.yamcs.getInstanceClient()!.getStreamUpdates(this.stream).then(response => {
      this.streaming$.next(true);
      this.streamSubscription = response.streamData$.subscribe(streamData => {
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
    });
  }

  stopStreaming() {
    if (this.streamSubscription) {
      this.streamSubscription.unsubscribe();
    }
    this.streaming$.next(false);
  }

  disconnect(collectionViewer: CollectionViewer) {
    if (this.streamSubscription) {
      this.streamSubscription.unsubscribe();
    }
    if (this.synchronizer) {
      window.clearInterval(this.synchronizer);
    }
    this.streamData$.complete();
    this.loading$.complete();
    this.streaming$.complete();
  }
}
