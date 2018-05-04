import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
} from '@angular/core';
import { YamcsService } from '../../core/services/YamcsService';
import { Observable } from 'rxjs/Observable';
import { TimeInfo, ConnectionInfo, Processor } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { Subscription } from 'rxjs/Subscription';

@Component({
  selector: 'app-processor-info',
  templateUrl: './ProcessorInfoComponent.html',
  styleUrls: ['./ProcessorInfoComponent.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProcessorInfoComponent implements OnDestroy {

  timeInfo$ = new BehaviorSubject<TimeInfo | null>(null);
  timeInfoSubscription: Subscription;

  processor$ = new BehaviorSubject <Processor | null>(null);
  processorSubscription: Subscription;

  connected$: Observable<boolean>;
  connectionInfo$: Observable<ConnectionInfo | null>;

  constructor(private yamcs: YamcsService) {
    this.yamcs.getInstanceClient()!.getTimeUpdates().then(response => {
      this.timeInfo$.next(response.timeInfo);
      this.timeInfoSubscription = response.timeInfo$.subscribe(timeInfo => {
        this.timeInfo$.next(timeInfo);
      });
    });

    this.yamcs.getInstanceClient()!.getProcessorUpdates().then(response => {
      this.processor$.next(response.processor);
      this.processorSubscription = response.processor$.subscribe(processor => {
        this.processor$.next(processor);
      });
    });

    this.connected$ = this.yamcs.getInstanceClient()!.connected$;
    this.connectionInfo$ = this.yamcs.connectionInfo$;
  }

  pauseReplay() {
    const processor = this.processor$.value!;
    this.yamcs.getInstanceClient()!.editReplayProcessor(processor.name, { state: 'paused' });
  }

  resumeReplay() {
    const processor = this.processor$.value!;
    this.yamcs.getInstanceClient()!.editReplayProcessor(processor.name, { state: 'running' });
  }

  ngOnDestroy() {
    if (this.timeInfoSubscription) {
      this.timeInfoSubscription.unsubscribe();
    }
    if (this.processorSubscription) {
      this.processorSubscription.unsubscribe();
    }
  }
}
