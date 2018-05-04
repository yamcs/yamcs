import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatDialog } from '@angular/material';
import { Processor } from '@yamcs/client';
import { BehaviorSubject, Subscription } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import { StartReplayDialog } from './StartReplayDialog';

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

  leaveReplay() {
    const processor = this.processor$.value!;
    const clientId = this.yamcs.getClientId();

    // Switch to the 'default' processor of the currently connected instance
    this.yamcs.yamcsClient.editClient(clientId, {
      instance: processor.instance
    });
  }

  ngOnDestroy() {
    if (this.processorSubscription) {
      this.processorSubscription.unsubscribe();
    }
  }
}
