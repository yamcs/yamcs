import { AfterViewInit, ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { MessageService, UserInfo, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { AdminPageTemplateComponent } from '../../shared/admin-page-template/admin-page-template.component';
import { AdminToolbarComponent } from '../../shared/admin-toolbar/admin-toolbar.component';
import { UsersTableComponent } from '../users-table/users-table.component';

@Component({
  standalone: true,
  templateUrl: './user-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdminPageTemplateComponent,
    AdminToolbarComponent,
    WebappSdkModule,
    UsersTableComponent,
  ],
})
export class UserListComponent implements AfterViewInit {

  filterControl = new UntypedFormControl();

  filterValue$ = new BehaviorSubject<string | null>(null);

  users$ = new BehaviorSubject<UserInfo[]>([]);
  activeUsers$: Observable<UserInfo[]>;
  activeUserCount$: Observable<number>;
  superUsers$: Observable<UserInfo[]>;
  superUserCount$: Observable<number>;
  internalUsers$: Observable<UserInfo[]>;
  internalUserCount$: Observable<number>;
  blockedUsers$: Observable<UserInfo[]>;
  blockedUserCount$: Observable<number>;

  constructor(
    private yamcs: YamcsService,
    title: Title,
    private route: ActivatedRoute,
    private router: Router,
    private messageService: MessageService,
  ) {
    title.setTitle('Users');
  }

  ngAfterViewInit() {
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.has('filter')) {
      this.filterControl.setValue(queryParams.get('filter'));
      this.filterValue$.next(queryParams.get('filter')!.toLowerCase());
    }

    this.filterControl.valueChanges.subscribe(() => {
      this.updateURL();
      const value = this.filterControl.value || '';
      this.filterValue$.next(value.toLowerCase());
    });

    this.activeUsers$ = this.users$.pipe(
      map(users => users.filter(u => u.active)),
    );
    this.activeUserCount$ = this.activeUsers$.pipe(
      map(users => users.length),
    );
    this.superUsers$ = this.users$.pipe(
      map(users => users.filter(u => u.superuser)),
    );
    this.superUserCount$ = this.superUsers$.pipe(
      map(users => users.length),
    );
    this.internalUsers$ = this.users$.pipe(
      map(users => users.filter(u => !u.identities)),
    );
    this.internalUserCount$ = this.internalUsers$.pipe(
      map(users => users.length),
    );
    this.blockedUsers$ = this.users$.pipe(
      map(users => users.filter(u => !u.active)),
    );
    this.blockedUserCount$ = this.blockedUsers$.pipe(
      map(users => users.length),
    );

    this.refresh();
  }

  private refresh() {
    this.yamcs.yamcsClient.getUsers().then(users => {
      this.users$.next(users);
    });
  }

  private updateURL() {
    const filterValue = this.filterControl.value;
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        filter: filterValue || null,
      },
      queryParamsHandling: 'merge',
    });
  }

  deleteUser(name: string) {
    if (confirm(`Are you sure you want to delete the user account '${name}'`)) {
      this.yamcs.yamcsClient.deleteUser(name)
        .then(() => this.refresh())
        .catch(err => this.messageService.showError(err));
    }
  }
}
