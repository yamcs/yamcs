import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { NavigationEnd, Router } from '@angular/router';
import { Observable, of, Subscription } from 'rxjs';
import { debounceTime, filter, map, switchMap } from 'rxjs/operators';
import { Parameter } from '../../client';
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

  searchControl = new FormControl(null);
  filteredOptions: Observable<Parameter[]>;

  user: User;
  sidebar$: Observable<boolean>;

  config: WebsiteConfig;

  telemetryActive = false;
  telemetryExpanded = false;
  commandingActive = false;
  commandingExpanded = false;
  mdbActive = false;
  mdbExpanded = false;
  archiveActive = false;
  archiveExpanded = false;

  telemetryItems: NavItem[] = [];
  commandingItems: NavItem[] = [];
  mdbItems: NavItem[] = [];
  archiveItems: NavItem[] = [];

  private routerSubscription: Subscription;

  constructor(
    readonly yamcs: YamcsService,
    configService: ConfigService,
    authService: AuthService,
    preferenceStore: PreferenceStore,
    private router: Router,
  ) {
    this.config = configService.getConfig();
    this.user = authService.getUser()!;

    if (this.config.features.tmArchive && this.user.hasAnyObjectPrivilegeOfType('ReadPacket')) {
      this.telemetryItems.push({ path: 'packets', label: 'Packets' });
    }
    if (this.user.hasSystemPrivilege('GetMissionDatabase')) {
      this.telemetryItems.push({ path: 'parameters', label: 'Parameters' });
    }
    if (this.user.hasObjectPrivilege('ReadBucket', 'displays')) {
      this.telemetryItems.push({ path: 'displays', label: 'Displays' });
    }
    for (const item of configService.getExtraNavItems('telemetry')) {
      if (item.condition && item.condition(this.user)) {
        this.telemetryItems.push(item);
      }
    }

    if (this.config.features.tc && this.user.hasAnyObjectPrivilegeOfType('Command')) {
      this.commandingItems.push({ path: 'send', label: 'Send a command' });
    }
    if (this.config.features.tc && this.user.hasAnyObjectPrivilegeOfType('Command')) {
      this.commandingItems.push({ path: 'stacks', label: 'Command Stacks' });
    }
    if (this.user.hasAnyObjectPrivilegeOfType('CommandHistory')) {
      this.commandingItems.push({ path: 'history', label: 'Command History' });
    }
    if (this.config.features.tc && this.user.hasSystemPrivilege('ControlCommandQueue')) {
      this.commandingItems.push({ path: 'queues', label: 'Queues' });
    }
    if (this.config.commandClearances && this.user.hasSystemPrivilege('ControlCommandClearances')) {
      this.commandingItems.push({ path: 'clearances', label: 'Clearances' });
    }
    for (const item of configService.getExtraNavItems('commanding')) {
      if (item.condition && item.condition(this.user)) {
        this.commandingItems.push(item);
      }
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

    if (this.user.hasAnyObjectPrivilegeOfType('ReadPacket')) {
      this.archiveItems.push({ path: 'overview', label: 'Overview' });
    }
    if (this.config.features.dass && this.user.hasSystemPrivilege('RequestPlayback')) {
      this.archiveItems.push({ path: 'gaps', label: 'Gaps' });
    }
    for (const item of configService.getExtraNavItems('archive')) {
      if (item.condition && item.condition(this.user)) {
        this.archiveItems.push(item);
      }
    }
    if (this.user.hasSystemPrivilege('ReadTables')) {
      this.archiveItems.push({ path: 'tables', label: 'Tables' });
    }
    if (this.user.hasAnyObjectPrivilegeOfType('Stream')) {
      this.archiveItems.push({ path: 'streams', label: 'Streams' });
    }

    this.sidebar$ = preferenceStore.sidebar$;

    this.routerSubscription = router.events.pipe(
      filter(evt => evt instanceof NavigationEnd)
    ).subscribe((evt: any) => {
      const url = evt.url as string;
      this.mdbActive = false;
      this.commandingActive = false;
      this.archiveActive = false;
      this.telemetryActive = false;
      this.collapseAllGroups();
      if (url.match(/\/mdb.*/)) {
        this.mdbActive = true;
        this.mdbExpanded = true;
      } else if (url.match(/\/commanding.*/)) {
        this.commandingActive = true;
        this.commandingExpanded = true;
      } else if (url.match(/\/archive.*/)) {
        this.archiveActive = true;
        this.archiveExpanded = true;
      } else if (url.match(/\/telemetry.*/)) {
        this.telemetryActive = true;
        this.telemetryExpanded = true;
      }
    });
  }

  ngOnInit() {
    this.filteredOptions = this.searchControl.valueChanges.pipe(
      debounceTime(300),
      switchMap(val => {
        if (val) {
          return this.yamcs.yamcsClient.getParameters(this.yamcs.instance!, { q: val, limit: 25 });
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
    this.mdbExpanded = false;
    this.archiveExpanded = false;
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

  toggleArchiveGroup() {
    const expanded = this.archiveExpanded;
    this.collapseAllGroups();
    this.archiveExpanded = !expanded;
  }

  showLinksItem() {
    return this.user.hasSystemPrivilege('ReadLinks');
  }

  showEventsItem() {
    return this.user.hasSystemPrivilege('ReadEvents');
  }

  showAlarmsItem() {
    return this.user.hasSystemPrivilege('ReadAlarms');
  }

  ngOnDestroy() {
    if (this.routerSubscription) {
      this.routerSubscription.unsubscribe();
    }
  }
}
