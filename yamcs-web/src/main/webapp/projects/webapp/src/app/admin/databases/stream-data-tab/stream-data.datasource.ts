import { DataSource } from '@angular/cdk/table';
import { StreamData, StreamSubscription, Synchronizer, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { StreamBuffer } from './StreamBuffer';

export class StreamDataDataSource extends DataSource<StreamData> {

  streamData$ = new BehaviorSubject<StreamData[]>([]);

  public loading$ = new BehaviorSubject<boolean>(false);
  public streaming$ = new BehaviorSubject<boolean>(false);
  public columns$ = new BehaviorSubject<string[]>([]);

  private streamSubscription: StreamSubscription;
  private streamBuffer = new StreamBuffer();

  private syncSubscription: Subscription;

  constructor(
    private yamcs: YamcsService,
    synchronizer: Synchronizer,
    private database: string,
    private stream: string,
  ) {
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
      instance: this.database,
      stream: this.stream,
    }, streamData => {
      if (!this.loading$.getValue()) {
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
