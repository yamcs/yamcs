import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, OnInit, ViewChild, effect } from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { NavigationEnd, Router } from '@angular/router';
import { ConfigService, ConnectionInfo, ExtensionService, MessageService, NavItem, Parameter, User, WebappSdkModule, WebsiteConfig, YamcsService } from '@yamcs/webapp-sdk';
import { Observable, Subscription, of } from 'rxjs';
import { debounceTime, filter, map, switchMap } from 'rxjs/operators';
import { AppearanceService } from '../../core/services/AppearanceService';
import { AuthService } from '../../core/services/AuthService';
import { ActivitiesLabelComponent } from '../activities-label/activities-label.component';
import { AlarmLabelComponent } from '../alarm-label/alarm-label.component';

@Component({
  standalone: true,
  templateUrl: './instance-page.component.html',
  styleUrl: './instance-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ActivitiesLabelComponent,
    AlarmLabelComponent,
    WebappSdkModule,
  ],
})
export class InstancePageComponent implements OnInit, OnDestroy {

  @ViewChild(MatSidenavContainer)
  pageContainer: MatSidenavContainer;

  @ViewChild(MatSidenavContent)
  pageContent: MatSidenavContent;

  @ViewChild('searchInput')
  searchInput: ElementRef<HTMLInputElement>;

  connectionInfo$: Observable<ConnectionInfo | null>;

  searchControl = new UntypedFormControl(null);
  filteredOptions: Observable<Parameter[]>;

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

  fullScreenMode$: Observable<boolean>;
  zenMode$: Observable<boolean>;

  private routerSubscription: Subscription;

  constructor(
    readonly yamcs: YamcsService,
    configService: ConfigService,
    authService: AuthService,
    appearanceService: AppearanceService,
    extensionService: ExtensionService,
    messageService: MessageService,
    private router: Router,
  ) {
    this.connectionInfo$ = this.yamcs.connectionInfo$;
    this.fullScreenMode$ = appearanceService.fullScreenMode$;
    this.zenMode$ = appearanceService.zenMode$;
    this.config = configService.getConfig();
    this.user = authService.getUser()!;

    effect(() => {
      if (appearanceService.fullScreenRequested()) {
        const el = this.pageContent.getElementRef().nativeElement;
        el.requestFullscreen().catch(err => messageService.showError(err));
      }
    });

    if (this.config.tmArchive && this.user.hasAnyObjectPrivilegeOfType('ReadPacket')) {
      this.telemetryItems.push({ path: 'packets', label: 'Packets' });
    }
    if (this.user.hasAnyObjectPrivilegeOfType('ReadParameter')) {
      this.telemetryItems.push({ path: 'parameters', label: 'Parameters' });
      if ((yamcs.connectionInfo$.value?.instance.capabilities ?? []).indexOf('parameter-lists') !== -1) {
        this.telemetryItems.push({ path: 'parameter-lists', label: 'Parameter lists' });
      }
    }
    const displayBucket = configService.getDisplayBucket();
    if (this.user.hasObjectPrivilege('ReadBucket', displayBucket)) {
      this.telemetryItems.push({ path: 'displays', label: 'Displays' });
    }
    for (const item of extensionService.getExtraNavItems('telemetry')) {
      if (item.condition && item.condition(this.user)) {
        this.telemetryItems.push(item);
      }
    }

    if (this.config.tc && this.user.hasAnyObjectPrivilegeOfType('Command')) {
      this.commandingItems.push({ path: 'send', label: 'Send a command' });
    }
    const stackBucket = configService.getStackBucket();
    if (this.config.tc && this.user.hasObjectPrivilege('ReadBucket', stackBucket)) {
      this.commandingItems.push({ path: 'stacks', label: 'Command stacks' });
    }
    if (this.user.hasAnyObjectPrivilegeOfType('CommandHistory')) {
      this.commandingItems.push({ path: 'history', label: 'Command history' });
    }
    if (this.config.tc && this.user.hasSystemPrivilege('ControlCommandQueue')) {
      this.commandingItems.push({ path: 'queues', label: 'Queues' });
    }
    if (this.config.commandClearanceEnabled && this.user.hasSystemPrivilege('ControlCommandClearances')) {
      this.commandingItems.push({ path: 'clearances', label: 'Clearances' });
    }
    for (const item of extensionService.getExtraNavItems('commanding')) {
      if (item.condition && item.condition(this.user)) {
        this.commandingItems.push(item);
      }
    }

    if (this.config.tc && this.user.hasObjectPrivilege('ReadBucket', stackBucket)) {
      this.proceduresItems.push({ path: 'stacks', label: 'Stacks' });
    }
    if (this.user.hasSystemPrivilege('ControlActivities')) {
      this.proceduresItems.push({ path: 'script', label: 'Run a script' });
    }
    for (const item of extensionService.getExtraNavItems('procedures')) {
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
      for (const item of extensionService.getExtraNavItems('mdb')) {
        if (item.condition && item.condition(this.user)) {
          this.mdbItems.push(item);
        }
      }
    }

    for (const item of extensionService.getExtraNavItems('archive')) {
      if (!item.condition || item.condition(this.user)) {
        this.extraItems.push(item);
      }
    }

    this.routerSubscription = router.events.pipe(
      filter(evt => evt instanceof NavigationEnd)
    ).subscribe((evt: any) => {
      const url = evt.url as string;
      this.mdbActive = false;
      this.commandingActive = false;
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

  ngOnInit() {
    this.filteredOptions = this.searchControl.valueChanges.pipe(
      debounceTime(300),
      switchMap(val => {
        if (val) {
          return this.yamcs.yamcsClient.getParameters(this.yamcs.instance!, {
            q: val,
            limit: 25,
            searchMembers: true,
          });
        } else {
          return of({ parameters: [] });
        }
      }),
      map(page => page.parameters || []),
    );
  }

  onSearchSelect(event: MatAutocompleteSelectedEvent) {
    this.searchControl.setValue('');
    this.router.navigate(['/telemetry/parameters' + event.option.value], {
      queryParams: { c: this.yamcs.context }
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

  handleKeydown(event: KeyboardEvent) {
    const el: HTMLInputElement = this.searchInput.nativeElement;
    if (event.key === "/" && this.isValidKeySource()) {
      el.focus();
      event.preventDefault();
    } else if (event.key === "Enter") {
      const value = this.searchControl.value;
      if (value) {
        this.searchControl.setValue('');
        this.router.navigate(['/search'], {
          queryParams: { c: this.yamcs.context, q: value },
        });
      }
    }
  }

  private isValidKeySource() {
    const { activeElement } = document;
    if (!activeElement) {
      return true;
    }
    return activeElement.tagName !== "INPUT"
      && activeElement.tagName !== "SELECT"
      && activeElement.tagName !== "TEXTAREA"
      && !activeElement.classList.contains('cm-content'); // Exclude CodeMirror editor
  }

  ngOnDestroy() {
    this.routerSubscription?.unsubscribe();
  }
}
