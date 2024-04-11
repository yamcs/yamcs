import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { Synchronizer, ThreadInfo, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subject, Subscription } from 'rxjs';
import { map } from 'rxjs/operators';
import { AdminPageTemplateComponent } from '../../shared/admin-page-template/admin-page-template.component';
import { AdminToolbarComponent } from '../../shared/admin-toolbar/admin-toolbar.component';
import { ThreadsTableComponent } from '../threads-table/threads-table.component';

@Component({
  standalone: true,
  templateUrl: './thread-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdminPageTemplateComponent,
    AdminToolbarComponent,
    WebappSdkModule,
    ThreadsTableComponent,
  ],
})
export class ThreadListComponent implements AfterViewInit, OnDestroy {

  filterControl = new UntypedFormControl();

  filterValue$ = new BehaviorSubject<string | null>(null);

  allThreads$ = new Subject<ThreadInfo[]>();

  allThreadCount$ = this.allThreads$.pipe(
    map(threads => threads.length),
  );
  runnableThreads$ = this.allThreads$.pipe(
    map(threads => threads.filter(t => t.state === 'RUNNABLE')),
  );
  runnableThreadCount$ = this.runnableThreads$.pipe(
    map(threads => threads.length),
  );
  timedWaitingThreads$ = this.allThreads$.pipe(
    map(threads => threads.filter(t => t.state === 'TIMED_WAITING')),
  );
  timedWaitingThreadCount$ = this.timedWaitingThreads$.pipe(
    map(threads => threads.length),
  );
  waitingThreads$ = this.allThreads$.pipe(
    map(threads => threads.filter(t => t.state === 'WAITING')),
  );
  waitingThreadCount$ = this.waitingThreads$.pipe(
    map(threads => threads.length),
  );
  blockedThreads$ = this.allThreads$.pipe(
    map(threads => threads.filter(t => t.state === 'BLOCKED')),
  );
  blockedThreadCount$ = this.blockedThreads$.pipe(
    map(threads => threads.length),
  );

  threadDumpURL: string;

  private syncSubscription: Subscription;

  constructor(
    private yamcs: YamcsService,
    title: Title,
    private route: ActivatedRoute,
    private router: Router,
    synchronizer: Synchronizer,
  ) {
    title.setTitle('Threads');
    this.threadDumpURL = yamcs.yamcsClient.getThreadDumpURL();

    this.refresh();
    this.syncSubscription = synchronizer.syncSlow(() => this.refresh());
  }

  private refresh() {
    this.yamcs.yamcsClient.getThreads().then(response => {
      this.allThreads$.next(response.threads || []);
    });
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

  ngOnDestroy() {
    this.syncSubscription?.unsubscribe();
  }
}
