import { AfterViewInit, ChangeDetectionStrategy, Component } from '@angular/core';
import { FormControl } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, from, Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { UserInfo } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './UsersPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsersPage implements AfterViewInit {

  filterControl = new FormControl();

  filterValue$ = new BehaviorSubject<string | null>(null);

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

    const users$ = from(this.yamcs.yamcsClient.getUsers());
    this.activeUsers$ = users$.pipe(
      map(users => users.filter(u => u.active)),
    );
    this.activeUserCount$ = this.activeUsers$.pipe(
      map(users => users.length),
    );
    this.superUsers$ = users$.pipe(
      map(users => users.filter(u => u.superuser)),
    );
    this.superUserCount$ = this.superUsers$.pipe(
      map(users => users.length),
    );
    this.internalUsers$ = users$.pipe(
      map(users => users.filter(u => !u.identities)),
    );
    this.internalUserCount$ = this.internalUsers$.pipe(
      map(users => users.length),
    );
    this.blockedUsers$ = users$.pipe(
      map(users => users.filter(u => !u.active)),
    );
    this.blockedUserCount$ = this.blockedUsers$.pipe(
      map(users => users.length),
    );
  }

  private updateURL() {
    const filterValue = this.filterControl.value;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        filter: filterValue || null,
      },
      queryParamsHandling: 'merge',
    });
  }
}
