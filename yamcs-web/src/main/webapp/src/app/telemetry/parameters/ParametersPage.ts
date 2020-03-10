import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatPaginator } from '@angular/material/paginator';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { GetParametersOptions } from '../../client';
import { Synchronizer } from '../../core/services/Synchronizer';
import { YamcsService } from '../../core/services/YamcsService';
import { ParametersDataSource } from './ParametersDataSource';

@Component({
  templateUrl: './ParametersPage.html',
  styleUrls: ['./ParametersPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParametersPage implements AfterViewInit, OnDestroy {

  instance: string;
  shortName = false;
  pageSize = 100;

  system: string | null = null;
  breadcrumb$ = new BehaviorSubject<BreadCrumbItem[]>([]);

  @ViewChild('top', { static: true })
  top: ElementRef;

  @ViewChild(MatPaginator, { static: true })
  paginator: MatPaginator;

  filterControl = new FormControl();

  dataSource: ParametersDataSource;

  displayedColumns = [
    'name',
    'engValue',
    'shortDescription',
  ];

  private queryParamMapSubscription: Subscription;

  constructor(
    yamcs: YamcsService,
    title: Title,
    private route: ActivatedRoute,
    private router: Router,
    synchronizer: Synchronizer,
  ) {
    title.setTitle('Parameters');
    this.instance = yamcs.getInstance();
    this.dataSource = new ParametersDataSource(yamcs, synchronizer);
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
    const options: GetParametersOptions = {
      system: this.system || '/',
      pos: this.paginator.pageIndex * this.pageSize,
      limit: this.pageSize,
    };
    const filterValue = this.filterControl.value;
    if (filterValue) {
      options.q = filterValue.toLowerCase();
    }
    this.dataSource.loadParameters(options).then(() => {
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
          route: '/telemetry/parameters',
          queryParams: { system: path, instance: this.instance },
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
