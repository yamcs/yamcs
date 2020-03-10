import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { BehaviorSubject, Observable, of, Subscription } from 'rxjs';
import { debounceTime, filter, map, switchMap } from 'rxjs/operators';
import { Parameter } from '../../client';
import { AuthService } from '../../core/services/AuthService';
import { ConfigService, WebsiteConfig } from '../../core/services/ConfigService';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { YamcsService } from '../../core/services/YamcsService';
import { User } from '../../shared/User';

@Component({
  templateUrl: './InstancePage.html',
  styleUrls: ['./InstancePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstancePage implements OnInit, OnDestroy {

  instance$ = new BehaviorSubject<string | null>(null);

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

  private routerSubscription: Subscription;

  constructor(
    private yamcs: YamcsService,
    configService: ConfigService,
    authService: AuthService,
    preferenceStore: PreferenceStore,
    route: ActivatedRoute,
    private router: Router,
  ) {
    this.config = configService.getConfig();
    this.user = authService.getUser()!;
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

    route.queryParams.subscribe(() => {
      this.instance$.next(yamcs.getInstance());
    });
  }

  ngOnInit() {
    this.filteredOptions = this.searchControl.valueChanges.pipe(
      debounceTime(300),
      switchMap(val => {
        if (val) {
          return this.yamcs.yamcsClient.getParameters(this.yamcs.getInstance(), { q: val, limit: 25 });
        } else {
          return of({ parameters: [] });
        }
      }),
      map(page => page.parameters || []),
    );
  }

  onSearchSelect(event: MatAutocompleteSelectedEvent) {
    const instance = this.yamcs.getInstance();
    this.searchControl.setValue('');
    this.router.navigate(['/telemetry/parameters/', event.option.value], {
      queryParams: { instance: instance, }
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

  showPacketsItem() {
    return this.config.features.tmArchive && this.user.hasAnyObjectPrivilegeOfType('ReadPacket');
  }

  showDisplaysItem() {
    return this.user.hasObjectPrivilege('ReadBucket', 'displays');
  }

  showParametersItem() {
    return this.user.hasSystemPrivilege('GetMissionDatabase');
  }

  showEventsItem() {
    return this.user.hasSystemPrivilege('ReadEvents');
  }

  showAlarmsItem() {
    return this.user.hasSystemPrivilege('ReadAlarms');
  }

  showTablesItem() {
    return this.user.hasSystemPrivilege('ReadTables');
  }

  showCommandQueuesItem() {
    return this.config.features.tc && this.user.hasSystemPrivilege('ControlCommandQueue');
  }

  showSendACommand() {
    return this.config.features.tc && this.user.hasAnyObjectPrivilegeOfType('Command');
  }

  showRunAStack() {
    return this.config.features.tc && this.user.hasAnyObjectPrivilegeOfType('Command');
  }

  showCommandHistory() {
    return this.user.hasAnyObjectPrivilegeOfType('CommandHistory');
  }

  showMDB() {
    return this.user.hasSystemPrivilege('GetMissionDatabase');
  }

  showArchiveOverview() {
    return this.user.hasAnyObjectPrivilegeOfType('ReadPacket');
  }

  showGapsItem() {
    return this.user.hasSystemPrivilege('RequestPlayback');
  }

  showStreamsItem() {
    return this.user.hasAnyObjectPrivilegeOfType('Stream');
  }

  showCommandClearancesItem() {
    return this.config.commandClearances && this.user.hasSystemPrivilege('ControlCommandClearances');
  }

  ngOnDestroy() {
    if (this.routerSubscription) {
      this.routerSubscription.unsubscribe();
    }
  }
}
