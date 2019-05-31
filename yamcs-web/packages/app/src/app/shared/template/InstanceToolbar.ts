import { ChangeDetectionStrategy, Component, Input, OnDestroy } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ConnectionInfo, Processor, TimeInfo } from '@yamcs/client';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { YamcsService } from '../../core/services/YamcsService';
import { SessionExpiredDialog } from '../dialogs/SessionExpiredDialog';
import { StartReplayDialog } from './StartReplayDialog';

@Component({
  selector: 'app-instance-toolbar',
  templateUrl: './InstanceToolbar.html',
  styleUrls: ['./InstanceToolbar.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstanceToolbar implements OnDestroy {

  @Input()
  hasDetailPane = false;

  processor$ = new BehaviorSubject<Processor | null>(null);
  processorSubscription: Subscription;

  timeInfo$ = new BehaviorSubject<TimeInfo | null>(null);
  timeInfoSubscription: Subscription;

  connected$: Observable<boolean>;
  connectionInfo$: Observable<ConnectionInfo | null>;
  showDetailPane$: Observable<boolean>;

  connectedSubscription: Subscription;

  constructor(
    private dialog: MatDialog,
    private yamcs: YamcsService,
    private snackBar: MatSnackBar,
    private preferenceStore: PreferenceStore,
  ) {
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

    this.connectedSubscription = this.connected$.subscribe(connected => {
      if (!connected) {
        dialog.open(SessionExpiredDialog);
      }
    });

    this.connectionInfo$ = this.yamcs.connectionInfo$;
    this.showDetailPane$ = preferenceStore.detailPane$;
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

  showDetailPane(enabled: boolean) {
    this.preferenceStore.setShowDetailPane(enabled);
  }

  ngOnDestroy() {
    if (this.processorSubscription) {
      this.processorSubscription.unsubscribe();
    }
    if (this.timeInfoSubscription) {
      this.timeInfoSubscription.unsubscribe();
    }
    if (this.connectedSubscription) {
      this.connectedSubscription.unsubscribe();
    }
  }
}
