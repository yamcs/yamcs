import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatDialog, MatSnackBar } from '@angular/material';
import { ConnectionInfo, Processor, TimeInfo } from '@yamcs/client';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
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

  timeInfo$ = new BehaviorSubject<TimeInfo | null>(null);
  timeInfoSubscription: Subscription;

  connected$: Observable<boolean>;
  connectionInfo$: Observable<ConnectionInfo | null>;

  constructor(private dialog: MatDialog, private yamcs: YamcsService, private snackBar: MatSnackBar) {
    this.yamcs.getInstanceClient()!.getProcessorUpdates().then(response => {
      this.processor$.next(response.processor);
      this.processorSubscription = response.processor$.subscribe(processor => {
        this.processor$.next(processor);
      });
    });

    this.yamcs.getInstanceClient()!.getTimeUpdates().then(response => {
      this.timeInfo$.next(response.timeInfo);
      this.timeInfoSubscription = response.timeInfo$.subscribe(timeInfo => {
        this.timeInfo$.next(timeInfo);
      });
    });

    this.connected$ = this.yamcs.getInstanceClient()!.connected$;
    this.connectionInfo$ = this.yamcs.connectionInfo$;
  }

  startReplay() {
    const dialogRef = this.dialog.open(StartReplayDialog, {
      width: '400px',
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.snackBar.open(`Initializing replay ${result.name}...`, undefined, {
          horizontalPosition: 'end',
        });
        this.yamcs.getInstanceClient()!.createProcessor(result).then(() => {
          this.snackBar.open(`Joined replay ${result.name}`, undefined, {
            duration: 3000,
            horizontalPosition: 'end',
          });
        }).catch(err => {
          this.snackBar.open(`Failed to initialize replay`, undefined, {
            duration: 3000,
            horizontalPosition: 'end',
          });
        });
      }
    });
  }

  pauseReplay() {
    const processor = this.processor$.value!;
    this.yamcs.getInstanceClient()!.editReplayProcessor(processor.name, { state: 'paused' });
  }

  resumeReplay() {
    const processor = this.processor$.value!;
    this.yamcs.getInstanceClient()!.editReplayProcessor(processor.name, { state: 'running' });
  }

  leaveReplay() {
    const processor = this.processor$.value!;
    const clientId = this.yamcs.getClientId();

    // Switch to the 'default' processor of the currently connected instance
    this.yamcs.yamcsClient.editClient(clientId, {
      instance: processor.instance
    });
  }

  changeSpeed(speed: string) {
    const processor = this.processor$.value!;
    this.yamcs.getInstanceClient()!.editReplayProcessor(processor.name, { speed });
  }

  ngOnDestroy() {
    if (this.processorSubscription) {
      this.processorSubscription.unsubscribe();
    }
    if (this.timeInfoSubscription) {
      this.timeInfoSubscription.unsubscribe();
    }
  }
}
