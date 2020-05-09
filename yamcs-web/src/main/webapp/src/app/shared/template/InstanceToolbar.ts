import { ChangeDetectionStrategy, Component, Input, OnDestroy } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { ConnectionInfo, Processor, ProcessorSubscription } from '../../client';
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
  processorSubscription: ProcessorSubscription;

  time$: Observable<string | null>;

  connected$: Observable<boolean>;
  connectionInfo$: Observable<ConnectionInfo | null>;
  showDetailPane$: Observable<boolean>;

  // For use in lazy dynamic population of Switch Processor menu.
  allProcessors$ = new BehaviorSubject<Processor[]>([]);

  private connectedSubscription: Subscription;

  constructor(
    private dialog: MatDialog,
    readonly yamcs: YamcsService,
    private snackBar: MatSnackBar,
    private preferenceStore: PreferenceStore,
    private router: Router,
  ) {
    this.processor$.next(yamcs.getProcessor());
    this.processorSubscription = this.yamcs.yamcsClient.createProcessorSubscription({
      instance: yamcs.instance!,
      processor: yamcs.processor!,
    }, processor => {
      this.processor$.next(processor);
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
          this.yamcs.switchContext(this.yamcs.instance!, result.name);
          this.snackBar.open(`Joining replay ${result.name}`, undefined, {
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
    this.yamcs.yamcsClient.editReplayProcessor(this.yamcs.instance!, this.yamcs.processor!, { state: 'paused' });
  }

  resumeReplay() {
    this.yamcs.yamcsClient.editReplayProcessor(this.yamcs.instance!, this.yamcs.processor!, { state: 'running' });
  }

  changeSpeed(speed: string) {
    this.yamcs.yamcsClient.editReplayProcessor(this.yamcs.instance!, this.yamcs.processor!, { speed });
  }

  showDetailPane(enabled: boolean) {
    this.preferenceStore.setShowDetailPane(enabled);
  }

  switchProcessorMenuOpened() {
    this.allProcessors$.next([]);
    this.yamcs.yamcsClient.getInstance(this.yamcs.instance!).then(instance => {
      this.allProcessors$.next(instance.processors || []);
    });
  }

  switchProcessor(processor: Processor) {
    this.yamcs.switchContext(this.yamcs.instance!, processor.name);
  }

  ngOnDestroy() {
    if (this.processorSubscription) {
      this.processorSubscription.cancel();
    }
    if (this.connectedSubscription) {
      this.connectedSubscription.unsubscribe();
    }
  }
}
