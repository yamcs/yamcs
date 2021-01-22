import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatPaginator } from '@angular/material/paginator';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { ConnectionInfo, GetCommandsOptions } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { CommandsDataSource } from './CommandsDataSource';

@Component({
  templateUrl: './SendCommandPage.html',
  styleUrls: ['./SendCommandPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SendCommandPage implements AfterViewInit, OnDestroy {

  connectionInfo$: Observable<ConnectionInfo | null>;

  pageSize = 100;

  system: string | null = null;
  breadcrumb$ = new BehaviorSubject<BreadCrumbItem[]>([]);

  @ViewChild('top', { static: true })
  top: ElementRef;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  filterControl = new FormControl();

  dataSource: CommandsDataSource;

  displayedColumns = [
    'name',
    'significance',
    'shortDescription',
  ];

  private queryParamMapSubscription: Subscription;

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
    private route: ActivatedRoute,
    private router: Router,
  ) {
    title.setTitle('Send a command');
    this.connectionInfo$ = yamcs.connectionInfo$;
    this.dataSource = new CommandsDataSource(yamcs);
  }

  ngAfterViewInit() {
    this.filterControl.setValue(this.route.snapshot.queryParamMap.get('filter'));
    this.changeSystem(this.route.snapshot.queryParamMap);

    this.queryParamMapSubscription = this.route.queryParamMap.subscribe(map => {
      if (map.get('system') !== this.system) {
        this.changeSystem(map);
      }
    });
    this.filterControl.valueChanges.subscribe(() => {
      this.paginator.pageIndex = 0;
      this.updateDataSource();
    });
    this.paginator.page.subscribe(() => {
      this.updateDataSource();
      this.top.nativeElement.scrollIntoView();
    });
  }

  changeSystem(map: ParamMap) {
    this.system = map.get('system');
    this.updateBrowsePath();

    if (map.has('page')) {
      this.paginator.pageIndex = Number(map.get('page'));
    } else {
      this.paginator.pageIndex = 0;
    }
    this.updateDataSource();
  }

  private updateDataSource() {
    this.updateURL();
    const options: GetCommandsOptions = {
      system: this.system || '/',
      noAbstract: true,
      pos: this.paginator.pageIndex * this.pageSize,
      limit: this.pageSize,
    };
    const filterValue = this.filterControl.value;
    if (filterValue) {
      options.q = filterValue.toLowerCase();
    }
    this.dataSource.loadCommands(options).then(() => {
      this.updateBrowsePath();
    });
  }

  private updateURL() {
    const filterValue = this.filterControl.value;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        page: this.paginator.pageIndex || null,
        filter: filterValue || null,
        system: this.system || null,
      },
      queryParamsHandling: 'merge',
    });
  }

  private updateBrowsePath() {
    const breadcrumb: BreadCrumbItem[] = [];
    let path = '';
    if (this.system) {
      for (const part of this.system.slice(1).split('/')) {
        path += '/' + part;
        breadcrumb.push({
          name: part,
          route: '/commanding/send',
          queryParams: { system: path, c: this.yamcs.context },
        });
      }
    }
    this.breadcrumb$.next(breadcrumb);
  }

  ngOnDestroy() {
    if (this.queryParamMapSubscription) {
      this.queryParamMapSubscription.unsubscribe();
    }
  }
}

export interface BreadCrumbItem {
  name?: string;
  route: string;
  queryParams: any;
}
