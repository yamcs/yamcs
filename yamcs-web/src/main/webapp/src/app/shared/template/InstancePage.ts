import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { NavigationEnd, Router } from '@angular/router';
import { Observable, of, Subscription } from 'rxjs';
import { debounceTime, filter, map, switchMap } from 'rxjs/operators';
import { ConnectionInfo, Parameter } from '../../client';
import { AuthService } from '../../core/services/AuthService';
import { ConfigService, NavItem, WebsiteConfig } from '../../core/services/ConfigService';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { YamcsService } from '../../core/services/YamcsService';
import { User } from '../../shared/User';

@Component({
  templateUrl: './InstancePage.html',
  styleUrls: ['./InstancePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstancePage implements OnInit, OnDestroy {

  connectionInfo$: Observable<ConnectionInfo | null>;

  searchControl = new UntypedFormControl(null);
  filteredOptions: Observable<Parameter[]>;

  user: User;
  sidebar$: Observable<boolean>;

  config: WebsiteConfig;

  telemetryActive = false;
  telemetryExpanded = false;
  commandingActive = false;
  commandingExpanded = false;
  timelineActive = false;
  timelineExpanded = false;
  mdbActive = false;
  mdbExpanded = false;

  telemetryItems: NavItem[] = [];
  commandingItems: NavItem[] = [];
  timelineItems: NavItem[] = [];
  mdbItems: NavItem[] = [];
  extraItems: NavItem[] = [];

  private routerSubscription: Subscription;

  constructor(
    readonly yamcs: YamcsService,
    configService: ConfigService,
    authService: AuthService,
    preferenceStore: PreferenceStore,
    private router: Router,
  ) {
    this.connectionInfo$ = this.yamcs.connectionInfo$;
    this.config = configService.getConfig();
    this.user = authService.getUser()!;

    if (this.config.tmArchive && this.user.hasAnyObjectPrivilegeOfType('ReadPacket')) {
      this.telemetryItems.push({ path: 'packets', label: 'Packets' });
    }
    if (this.user.hasAnyObjectPrivilegeOfType('ReadParameter')) {
      this.telemetryItems.push({ path: 'parameters', label: 'Parameters' });
    }
    if (this.user.hasObjectPrivilege('ReadBucket', this.config.displayBucket)) {
      this.telemetryItems.push({ path: 'displays', label: 'Displays' });
    }
    for (const item of configService.getExtraNavItems('telemetry')) {
      if (item.condition && item.condition(this.user)) {
        this.telemetryItems.push(item);
      }
    }

    if (this.config.tc && this.user.hasAnyObjectPrivilegeOfType('Command')) {
      this.commandingItems.push({ path: 'send', label: 'Send a command' });
    }
    if (this.config.tc && this.user.hasObjectPrivilege('ReadBucket', this.config.stackBucket)) {
      this.commandingItems.push({ path: 'stacks', label: 'Command Stacks' });
    }
    if (this.user.hasAnyObjectPrivilegeOfType('CommandHistory')) {
      this.commandingItems.push({ path: 'history', label: 'Command History' });
    }
    if (this.config.tc && this.user.hasSystemPrivilege('ControlCommandQueue')) {
      this.commandingItems.push({ path: 'queues', label: 'Queues' });
    }
    if (this.config.commandClearanceEnabled && this.user.hasSystemPrivilege('ControlCommandClearances')) {
      this.commandingItems.push({ path: 'clearances', label: 'Clearances' });
    }
    for (const item of configService.getExtraNavItems('commanding')) {
      if (item.condition && item.condition(this.user)) {
        this.commandingItems.push(item);
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
      this.mdbItems.push({ path: 'containers', label: 'Containers' });
      this.mdbItems.push({ path: 'commands', label: 'Commands' });
      this.mdbItems.push({ path: 'algorithms', label: 'Algorithms' });
      for (const item of configService.getExtraNavItems('mdb')) {
        if (item.condition && item.condition(this.user)) {
          this.mdbItems.push(item);
        }
      }
    }

    if (this.config.dass && this.user.hasSystemPrivilege('RequestPlayback')) {
      this.extraItems.push({ path: 'gaps', label: 'Gaps' });
    }
    for (const item of configService.getExtraNavItems('archive')) {
      if (item.condition && item.condition(this.user)) {
        this.extraItems.push(item);
      }
    }

    this.sidebar$ = preferenceStore.sidebar$;

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
    this.router.navigate(['/telemetry/parameters/', event.option.value], {
      queryParams: { c: this.yamcs.context }
    });
  }

  private collapseAllGroups() {
    this.telemetryExpanded = false;
    this.commandingExpanded = false;
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
    return this.user.hasSystemPrivilege('ControlFileTransfers');
  }

  showArchiveBrowserItem() {
    return this.user.hasAnyObjectPrivilegeOfType('ReadPacket');
  }

  ngOnDestroy() {
    if (this.routerSubscription) {
      this.routerSubscription.unsubscribe();
    }
  }
}
