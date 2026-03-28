import {
  ChangeDetectionStrategy,
  Component,
  effect,
  ElementRef,
  OnDestroy,
  Signal,
  ViewChild,
} from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import {
  AppearanceService,
  AuthService,
  ConfigService,
  ConnectionInfo,
  ExtensionService,
  MessageService,
  NavItem,
  Preferences,
  User,
  WebappSdkModule,
  WebsiteConfig,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { Observable, Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { ActivitiesLabelComponent } from '../activities-label/activities-label.component';
import { AlarmLabelComponent } from '../alarm-label/alarm-label.component';

@Component({
  templateUrl: './instance-layout.component.html',
  styleUrl: './instance-layout.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ActivitiesLabelComponent, AlarmLabelComponent, WebappSdkModule],
  host: {
    '[class.collapsed]': 'collapsed()',
  },
})
export class InstanceLayoutComponent implements OnDestroy {
  @ViewChild('pageContent')
  pageContent: ElementRef<HTMLElement>;

  connectionInfo$: Observable<ConnectionInfo | null>;

  user: User;

  config: WebsiteConfig;

  telemetryActive = false;
  telemetryExpanded = false;
  commandingActive = false;
  commandingExpanded = false;
  proceduresActive = false;
  proceduresExpanded = false;
  timelineActive = false;
  timelineExpanded = false;
  mdbActive = false;
  mdbExpanded = false;

  telemetryItems: NavItem[] = [];
  commandingItems: NavItem[] = [];
  proceduresItems: NavItem[] = [];
  timelineItems: NavItem[] = [];
  mdbItems: NavItem[] = [];
  extraItems: NavItem[] = [];

  collapsed: Signal<boolean>;
  fullScreenMode$: Observable<boolean>;
  focusMode$: Observable<boolean>;

  private routerSubscription: Subscription;

  constructor(
    readonly yamcs: YamcsService,
    configService: ConfigService,
    authService: AuthService,
    appearanceService: AppearanceService,
    prefs: Preferences,
    extensionService: ExtensionService,
    messageService: MessageService,
    router: Router,
  ) {
    this.connectionInfo$ = this.yamcs.connectionInfo$;
    this.collapsed = prefs.watchBoolean('sidenav.collapsed', false);

    this.fullScreenMode$ = appearanceService.fullScreenMode$;
    this.focusMode$ = appearanceService.focusMode$;
    this.config = configService.getConfig();
    this.user = authService.getUser()!;

    effect(() => {
      if (appearanceService.fullScreenRequested()) {
        const el = this.pageContent.nativeElement;
        el.requestFullscreen().catch((err) => messageService.showError(err));
      }
    });

    if (
      this.config.tmArchive &&
      this.user.hasAnyObjectPrivilegeOfType('ReadPacket')
    ) {
      this.telemetryItems.push({ path: 'packets', label: 'Packets' });
    }
    if (this.user.hasAnyObjectPrivilegeOfType('ReadParameter')) {
      this.telemetryItems.push({ path: 'parameters', label: 'Parameters' });
      if (
        (yamcs.connectionInfo$.value?.instance.capabilities ?? []).indexOf(
          'parameter-lists',
        ) !== -1
      ) {
        this.telemetryItems.push({
          path: 'parameter-lists',
          label: 'Parameter lists',
        });
      }
    }
    const displayBucket = configService.getDisplayBucket();
    const mayReadDisplayBucket =
      this.user.hasObjectPrivilege('ReadBucket', displayBucket) ||
      this.user.hasObjectPrivilege('ManageBucket', displayBucket) ||
      this.user.hasSystemPrivilege('ManageAnyBucket');
    if (mayReadDisplayBucket) {
      this.telemetryItems.push({ path: 'displays', label: 'Displays' });
    }
    for (const item of extensionService.getNavItems('telemetry')) {
      if (item.condition && item.condition(this.user)) {
        this.telemetryItems.push(item);
      }
    }

    if (this.config.tc && this.user.hasAnyObjectPrivilegeOfType('Command')) {
      this.commandingItems.push({ path: 'send', label: 'Send a command' });
    }
    const stackBucket = configService.getStackBucket();
    if (this.user.hasAnyObjectPrivilegeOfType('CommandHistory')) {
      this.commandingItems.push({ path: 'history', label: 'Command history' });
    }
    if (this.config.tc && this.user.hasSystemPrivilege('ControlCommandQueue')) {
      this.commandingItems.push({ path: 'queues', label: 'Queues' });
    }
    if (
      this.config.commandClearanceEnabled &&
      this.user.hasSystemPrivilege('ControlCommandClearances')
    ) {
      this.commandingItems.push({ path: 'clearances', label: 'Clearances' });
    }
    for (const item of extensionService.getNavItems('commanding')) {
      if (item.condition && item.condition(this.user)) {
        this.commandingItems.push(item);
      }
    }

    const mayReadStackBucket =
      this.user.hasObjectPrivilege('ReadBucket', stackBucket) ||
      this.user.hasObjectPrivilege('ManageBucket', stackBucket) ||
      this.user.hasSystemPrivilege('ManageAnyBucket');
    if (this.config.tc && mayReadStackBucket) {
      this.proceduresItems.push({ path: 'stacks', label: 'Stacks' });
    }
    if (
      this.user.hasSystemPrivilege('ControlActivities') &&
      (yamcs.connectionInfo$.value?.instance.capabilities ?? []).indexOf(
        'activities',
      ) !== -1
    ) {
      this.proceduresItems.push({ path: 'script', label: 'Run a script' });
    }
    for (const item of extensionService.getNavItems('procedures')) {
      if (item.condition && item.condition(this.user)) {
        this.proceduresItems.push(item);
      }
    }

    if (this.user.hasSystemPrivilege('ReadTimeline')) {
      this.timelineItems.push({ path: 'chart', label: 'Chart' });
    }
    if (this.user.hasSystemPrivilege('ControlTimeline')) {
      this.timelineItems.push({ path: 'views', label: 'Views' });
      this.timelineItems.push({ path: 'bands', label: 'Bands' });
      this.timelineItems.push({ path: 'items', label: 'Items' });
    }

    if (this.user.hasSystemPrivilege('GetMissionDatabase')) {
      this.mdbItems.push({ path: '', label: 'Overview' });
      this.mdbItems.push({ path: 'parameters', label: 'Parameters' });
      this.mdbItems.push({ path: 'parameter-types', label: 'Parameter types' });
      this.mdbItems.push({ path: 'containers', label: 'Containers' });
      this.mdbItems.push({ path: 'commands', label: 'Commands' });
      this.mdbItems.push({ path: 'algorithms', label: 'Algorithms' });
      for (const item of extensionService.getNavItems('mdb')) {
        if (item.condition && item.condition(this.user)) {
          this.mdbItems.push(item);
        }
      }
    }

    for (const item of extensionService.getNavItems('archive')) {
      if (!item.condition || item.condition(this.user)) {
        this.extraItems.push(item);
      }
    }

    this.routerSubscription = router.events
      .pipe(filter((evt) => evt instanceof NavigationEnd))
      .subscribe((evt: any) => {
        const url = evt.url as string;
        this.mdbActive = false;
        this.commandingActive = false;
        this.proceduresActive = false;
        this.telemetryActive = false;
        this.timelineActive = false;
        this.collapseAllGroups();
        if (url.match(/\/mdb.*/)) {
          this.mdbActive = true;
          this.mdbExpanded = true;
        } else if (url.match(/\/commanding.*/)) {
          this.commandingActive = true;
          this.commandingExpanded = true;
        } else if (url.match(/\/procedures.*/)) {
          this.proceduresActive = true;
          this.proceduresExpanded = true;
        } else if (url.match(/\/telemetry.*/)) {
          this.telemetryActive = true;
          this.telemetryExpanded = true;
        } else if (url.match(/\/timeline.*/)) {
          this.timelineActive = true;
          this.timelineExpanded = true;
        }
      });
  }

  private collapseAllGroups() {
    this.telemetryExpanded = false;
    this.commandingExpanded = false;
    this.proceduresExpanded = false;
    this.timelineExpanded = false;
    this.mdbExpanded = false;
  }

  toggleTelemetryGroup() {
    const expanded = this.telemetryExpanded;
    this.collapseAllGroups();
    this.telemetryExpanded = !expanded;
  }

  toggleCommandingGroup() {
    const expanded = this.commandingExpanded;
    this.collapseAllGroups();
    this.commandingExpanded = !expanded;
  }

  toggleProceduresGroup() {
    const expanded = this.proceduresExpanded;
    this.collapseAllGroups();
    this.proceduresExpanded = !expanded;
  }

  toggleMdbGroup() {
    const expanded = this.mdbExpanded;
    this.collapseAllGroups();
    this.mdbExpanded = !expanded;
  }

  toggleTimelineGroup() {
    const expanded = this.timelineExpanded;
    this.collapseAllGroups();
    this.timelineExpanded = !expanded;
  }

  showLinksItem() {
    return this.user.hasSystemPrivilege('ReadLinks');
  }

  showAlgorithmsItem() {
    return this.user.hasAnyObjectPrivilegeOfType('ReadAlgorithm');
  }

  showEventsItem() {
    return this.user.hasSystemPrivilege('ReadEvents');
  }

  showAlarmsItem() {
    return this.user.hasSystemPrivilege('ReadAlarms');
  }

  showFileTransferItem() {
    return this.user.hasSystemPrivilege('ReadFileTransfers');
  }

  showActivitiesItem() {
    return this.user.hasSystemPrivilege('ReadActivities');
  }

  showArchiveBrowserItem() {
    return this.user.hasAnyObjectPrivilegeOfType('ReadPacket');
  }

  ngOnDestroy() {
    this.routerSubscription?.unsubscribe();
  }
}
