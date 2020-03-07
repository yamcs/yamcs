import { ChangeDetectionStrategy, Component, Input, OnDestroy } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { ConnectionInfo, Processor } from '../../client';
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

  time$: Observable<string | null>;

  connected$: Observable<boolean>;
  connectionInfo$: Observable<ConnectionInfo | null>;
  showDetailPane$: Observable<boolean>;

  private connectedSubscription: Subscription;

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

    this.connected$ = this.yamcs.yamcsClient.connected$;
    this.time$ = this.yamcs.time$;

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
        this.yamcs.yamcsClient.createProcessor(result).then(() => {
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
    this.yamcs.yamcsClient.editReplayProcessor(processor.instance, processor.name, { state: 'paused' });
  }

  resumeReplay() {
    const processor = this.processor$.value!;
    this.yamcs.yamcsClient.editReplayProcessor(processor.instance, processor.name, { state: 'running' });
  }

  changeSpeed(speed: string) {
    const processor = this.processor$.value!;
    this.yamcs.yamcsClient.editReplayProcessor(processor.instance, processor.name, { speed });
  }

  showDetailPane(enabled: boolean) {
    this.preferenceStore.setShowDetailPane(enabled);
  }

  ngOnDestroy() {
    if (this.processorSubscription) {
      this.processorSubscription.unsubscribe();
    }
    if (this.connectedSubscription) {
      this.connectedSubscription.unsubscribe();
    }
  }
}
