import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
} from '@angular/core';
import { MatDialog } from '@angular/material';
import { StartReplayDialog } from './StartReplayDialog';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { Processor } from '@yamcs/client';
import { Subscription } from 'rxjs/Subscription';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-monitor-toolbar',
  templateUrl: './MonitorToolbar.html',
  styleUrls: ['./MonitorToolbar.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MonitorToolbar implements OnDestroy {

  processor$ = new BehaviorSubject<Processor | null>(null);
  processorSubscription: Subscription;

  constructor(private dialog: MatDialog, private yamcs: YamcsService) {
    this.yamcs.getInstanceClient()!.getProcessorUpdates().then(response => {
      this.processor$.next(response.processor);
      this.processorSubscription = response.processor$.subscribe(processor => {
        this.processor$.next(processor);
      });
    });
  }

  startReplay() {
    this.dialog.open(StartReplayDialog, {
      width: '400px',
    });
  }

  stopReplay() {
    const processor = this.processor$.value!;
    this.yamcs.getInstanceClient()!.deleteReplayProcessor(processor.name);
  }

  ngOnDestroy() {
    if (this.processorSubscription) {
      this.processorSubscription.unsubscribe();
    }
  }
}
