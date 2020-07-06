import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { Route } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './RoutesPage.html',
  styleUrls: ['./RoutesPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RoutesPage implements AfterViewInit {

  filterControl = new FormControl();

  @ViewChild(MatSort, { static: true })
  sort: MatSort;

  displayedColumns = [
    // 'service',
    'method',
    'requestCount',
    'errorCount',
    'http',
  ];

  dataSource = new MatTableDataSource<Route>();

  selectedRoute$ = new BehaviorSubject<Route | null>(null);

  constructor(
    private yamcs: YamcsService,
    title: Title,
    private route: ActivatedRoute,
    private router: Router,
  ) {
    title.setTitle('API routes');
    this.dataSource.filterPredicate = (rec, filter) => {
      return rec.url.toLowerCase().indexOf(filter) >= 0;
    };
  }

  ngAfterViewInit() {
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.has('filter')) {
      this.filterControl.setValue(queryParams.get('filter'));
      this.dataSource.filter = queryParams.get('filter')!.toLowerCase();
    }

    this.filterControl.valueChanges.subscribe(() => {
      this.updateURL();
      const value = this.filterControl.value || '';
      this.dataSource.filter = value.toLowerCase();
    });

    this.refresh();

    this.dataSource.sort = this.sort;
  }

  refresh() {
    this.yamcs.yamcsClient.getRoutes().then(page => {
      this.dataSource.data = page.routes || [];
    });
  }

  selectRoute(route: Route) {
    this.selectedRoute$.next(route);
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
