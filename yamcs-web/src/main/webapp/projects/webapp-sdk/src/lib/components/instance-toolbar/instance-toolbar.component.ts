import { CdkPortalOutlet } from '@angular/cdk/portal';
import { AsyncPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ContentChild,
  input,
  OnDestroy,
} from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { MatDivider } from '@angular/material/list';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltip } from '@angular/material/tooltip';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { BaseComponent } from '../../abc/BaseComponent';
import { ConnectionInfo, Processor, ProcessorSubscription } from '../../client';
import { DateTimePipe } from '../../pipes/datetime.pipe';
import { DurationPipe } from '../../pipes/duration.pipe';
import { YaIconAction } from '../icon-action/icon-action.component';
import { YaPageButton } from '../page-button/page-button.component';
import { YaTextAction } from '../text-action/text-action.component';
import {
  YA_INSTANCE_TOOLBAR,
  YaInstanceToolbarLabel,
} from './instance-toolbar-label.directive';
import { SessionExpiredDialogComponent } from './session-expired-dialog.component';
import { StartReplayDialogComponent } from './start-replay-dialog.component';

@Component({
  selector: 'ya-instance-toolbar',
  templateUrl: './instance-toolbar.component.html',
  styleUrl: './instance-toolbar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: YA_INSTANCE_TOOLBAR,
      useExisting: YaInstanceToolbar,
    },
  ],
  host: {
    class: 'ya-instance-toolbar',
  },
  imports: [
    AsyncPipe,
    CdkPortalOutlet,
    DateTimePipe,
    DurationPipe,
    MatDivider,
    MatIcon,
    MatMenu,
    MatMenuItem,
    MatMenuTrigger,
    MatTooltip,
    YaIconAction,
    YaPageButton,
    YaTextAction,
  ],
})
export class YaInstanceToolbar extends BaseComponent implements OnDestroy {
  // Plain text label, used when there is no template label
  textLabel = input<string | undefined>(undefined, { alias: 'label' });

  private _templateLabel: YaInstanceToolbarLabel;

  // Content for the attr label given by `<ng-template ya-instance-toolbar-label>`
  @ContentChild(YaInstanceToolbarLabel)
  get templateLabel(): YaInstanceToolbarLabel {
    return this._templateLabel;
  }
  set templateLabel(value: YaInstanceToolbarLabel | undefined) {
    if (value && value._closestToolbar === this) {
      this._templateLabel = value;
    }
  }

  processor$ = new BehaviorSubject<Processor | null>(null);
  processorSubscription: ProcessorSubscription;

  time$: Observable<string | null>;

  showRange = computed(() => {
    const m = this.routeData();
    return m.get('showRangeSelector') === true;
  });
  range$: Observable<string>;

  connected$: Observable<boolean>;
  connectionInfo$: Observable<ConnectionInfo | null>;
  fullScreenMode$: Observable<boolean>;
  focusMode$: Observable<boolean>;

  // For use in lazy dynamic population of Switch Processor menu.
  allProcessors$ = new BehaviorSubject<Processor[]>([]);

  private connectedSubscription: Subscription;

  constructor(
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
  ) {
    super();
    const { yamcs } = this;
    this.processor$.next(yamcs.getProcessor());
    if (yamcs.processor) {
      this.processorSubscription =
        yamcs.yamcsClient.createProcessorSubscription(
          {
            instance: yamcs.instance!,
            processor: yamcs.processor,
          },
          (processor) => {
            this.processor$.next(processor);
          },
        );
    }

    this.connected$ = this.yamcs.yamcsClient.connected$;
    this.time$ = this.yamcs.time$;
    this.range$ = this.yamcs.range$;
    this.fullScreenMode$ = this.appearanceService.fullScreenMode$;
    this.focusMode$ = this.appearanceService.focusMode$;

    this.connectedSubscription = this.connected$.subscribe((connected) => {
      if (!connected && this.authService.user$.value) {
        dialog.open(SessionExpiredDialogComponent, {
          disableClose: true,
          width: '400px',
          height: '200px',
        });
      }
    });

    this.connectionInfo$ = this.yamcs.connectionInfo$;
  }

  startReplay() {
    this.dialog
      .open(StartReplayDialogComponent, {
        width: '400px',
      })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.snackBar.open(
            `Initializing replay ${result.name}...`,
            undefined,
            {
              horizontalPosition: 'end',
            },
          );
          this.yamcs.yamcsClient
            .createProcessor(result)
            .then(() => {
              this.yamcs.switchContext(this.yamcs.instance!, result.name);
              this.snackBar.open(`Joining replay ${result.name}`, undefined, {
                duration: 3000,
                horizontalPosition: 'end',
              });
            })
            .catch((err) => {
              this.snackBar.open(`Failed to initialize replay`, undefined, {
                duration: 3000,
                horizontalPosition: 'end',
              });
            });
        }
      });
  }

  pauseReplay() {
    this.yamcs.yamcsClient
      .editReplayProcessor(this.yamcs.instance!, this.yamcs.processor!, {
        state: 'paused',
      })
      .catch((err) => this.messageService.showError(err));
  }

  resumeReplay() {
    this.yamcs.yamcsClient
      .editReplayProcessor(this.yamcs.instance!, this.yamcs.processor!, {
        state: 'running',
      })
      .catch((err) => this.messageService.showError(err));
  }

  changeSpeed(speed: string) {
    this.yamcs.yamcsClient
      .editReplayProcessor(this.yamcs.instance!, this.yamcs.processor!, {
        speed,
      })
      .catch((err) => this.messageService.showError(err));
  }

  async leaveAndCloseReplay() {
    const instance = this.yamcs.instance!;
    const processor = this.yamcs.processor!;
    try {
      await this.yamcs.switchContext(instance);
      await this.yamcs.yamcsClient.deleteReplayProcessor(instance, processor);
    } catch (err: any) {
      this.messageService.showError(err);
    }
  }

  switchProcessorMenuOpened() {
    this.allProcessors$.next([]);
    this.yamcs.yamcsClient
      .getInstance(this.yamcs.instance!)
      .then((instance) => {
        this.allProcessors$.next(instance.processors || []);
      });
  }

  enterFocusMode() {
    this.appearanceService.focusMode$.next(true);
  }

  exitFocusMode() {
    this.appearanceService.focusMode$.next(false);
  }

  enterFullScreen() {
    this.appearanceService.fullScreenRequested.set(true);
    this.appearanceService.fullScreenMode$.next(true);
  }

  exitFullScreen() {
    this.appearanceService.fullScreenRequested.set(false);
    this.appearanceService.fullScreenMode$.next(false);
  }

  switchProcessor(processor: Processor) {
    this.yamcs
      .switchContext(this.yamcs.instance!, processor.name)
      .catch((err) => this.messageService.showError(err));
  }

  ngOnDestroy() {
    this.processorSubscription?.cancel();
    this.connectedSubscription?.unsubscribe();
  }
}
