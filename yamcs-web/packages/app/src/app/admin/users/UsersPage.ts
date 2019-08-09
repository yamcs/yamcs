import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, ViewChild } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { UserInfo } from '@yamcs/client';
import { BehaviorSubject, from, fromEvent, Observable } from 'rxjs';
import { debounceTime, distinctUntilChanged, map } from 'rxjs/operators';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './UsersPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsersPage implements AfterViewInit {

  @ViewChild('filter', { static: true })
  filter: ElementRef;

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
      this.filter.nativeElement.value = queryParams.get('filter');
      this.filterValue$.next(queryParams.get('filter')!.toLowerCase());
    }

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

    fromEvent(this.filter.nativeElement, 'keyup').pipe(
      debounceTime(150), // Keep low -- Client-side filter
      map(() => this.filter.nativeElement.value.trim()), // Detect 'distinct' on value not on KeyEvent
      distinctUntilChanged(),
    ).subscribe(value => {
      this.updateURL();
      this.filterValue$.next(value.toLowerCase());
    });
  }

  private updateURL() {
    const filterValue = this.filter.nativeElement.value.trim();
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        filter: filterValue || null,
      },
      queryParamsHandling: 'merge',
    });
  }
}
