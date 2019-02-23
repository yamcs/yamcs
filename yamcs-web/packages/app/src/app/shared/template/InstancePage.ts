import { ChangeDetectionStrategy, Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatAutocompleteSelectedEvent } from '@angular/material';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { Instance, Parameter } from '@yamcs/client';
import { BehaviorSubject, Observable, of, Subscription } from 'rxjs';
import { debounceTime, filter, map, switchMap } from 'rxjs/operators';
import { AppConfig, APP_CONFIG, SidebarItem } from '../../core/config/AppConfig';
import { AuthService } from '../../core/services/AuthService';
import { YamcsService } from '../../core/services/YamcsService';
import { User } from '../../shared/User';

@Component({
  templateUrl: './InstancePage.html',
  styleUrls: ['./InstancePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstancePage implements OnInit, OnDestroy {

  instance$ = new BehaviorSubject<Instance | null>(null);

  searchControl = new FormControl(null);
  filteredOptions: Observable<Parameter[]>;

  user: User;

  extraItems: SidebarItem[];

  monitoringActive = false;
  monitoringExpanded = false;
  commandingActive = false;
  commandingExpanded = false;
  mdbActive = false;
  mdbExpanded = false;
  systemActive = false;
  systemExpanded = false;

  private routerSubscription: Subscription;

  constructor(
    private yamcs: YamcsService,
    @Inject(APP_CONFIG) appConfig: AppConfig,
    authService: AuthService,
    route: ActivatedRoute,
    private router: Router,
  ) {
    const monitorConfig = appConfig.monitor || {};
    this.extraItems = monitorConfig.extraItems || [];

    this.user = authService.getUser()!;

    this.routerSubscription = router.events.pipe(
      filter(evt => evt instanceof NavigationEnd)
    ).subscribe((evt: any) => {
      const url = evt.url as string;
      this.collapseAllGroups();
      if (url.match(/\/mdb.*/)) {
        this.mdbActive = true;
        this.mdbExpanded = true;
      } else if (url.match(/\/commanding.*/)) {
        this.commandingActive = true;
        this.commandingExpanded = true;
      } else if (url.match(/\/system.*/)) {
        this.systemActive = true;
        this.systemExpanded = true;
      } else if (url.match(/\/monitor.*/)) {
        this.monitoringActive = true;
        this.monitoringExpanded = true;
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
          return this.yamcs.getInstanceClient()!.getParameters({ q: val, limit: 25 });
        } else {
          return of({ parameter: [] });
        }
      }),
      map(page => page.parameter || []),
    );
  }

  onSearchSelect(event: MatAutocompleteSelectedEvent) {
    const instance = this.yamcs.getInstance();
    this.searchControl.setValue('');
    this.router.navigate(['/mdb/parameters/', event.option.value], {
      queryParams: { instance: instance.name, }
    });
  }

  private collapseAllGroups() {
    this.monitoringExpanded = false;
    this.commandingExpanded = false;
    this.mdbExpanded = false;
    this.systemExpanded = false;
  }

  toggleMonitoringGroup() {
    const expanded = this.monitoringExpanded;
    this.collapseAllGroups();
    this.monitoringExpanded = !expanded;
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

  toggleSystemGroup() {
    const expanded = this.systemExpanded;
    this.collapseAllGroups();
    this.systemExpanded = !expanded;
  }

  showEventsItem() {
    return this.user.hasSystemPrivilege('ReadEvents');
  }

  showServicesItem() {
    return this.user.hasSystemPrivilege('ControlServices');
  }

  showTablesItem() {
    return this.user.hasSystemPrivilege('ReadTables');
  }

  showStreamsItem() {
    const objectPrivileges = this.user.getObjectPrivileges();
    for (const priv of objectPrivileges) {
      if (priv.type === 'Stream') {
        return true;
      }
    }
    return this.user.isSuperuser();
  }

  ngOnDestroy() {
    if (this.routerSubscription) {
      this.routerSubscription.unsubscribe();
    }
  }
}
