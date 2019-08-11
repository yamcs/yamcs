import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { Endpoint } from '@yamcs/client';
import { BehaviorSubject, fromEvent } from 'rxjs';
import { debounceTime, distinctUntilChanged, map } from 'rxjs/operators';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './EndpointsPage.html',
  styleUrls: ['./EndpointsPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EndpointsPage implements AfterViewInit {

  @ViewChild('filter', { static: true })
  filter: ElementRef;

  @ViewChild(MatSort, { static: true })
  sort: MatSort;

  displayedColumns = [
    'service',
    'method',
    'requestCount',
    'errorCount',
    'http',
  ];

  dataSource = new MatTableDataSource<Endpoint>();

  selectedEndpoint$ = new BehaviorSubject<Endpoint | null>(null);

  constructor(
    private yamcs: YamcsService,
    title: Title,
    private route: ActivatedRoute,
    private router: Router,
  ) {
    title.setTitle('API Endpoints');
    this.dataSource.filterPredicate = (endpoint, filter) => {
      return endpoint.url.toLowerCase().indexOf(filter) >= 0;
    };
  }

  ngAfterViewInit() {
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.has('filter')) {
      this.filter.nativeElement.value = queryParams.get('filter');
      this.dataSource.filter = queryParams.get('filter')!.toLowerCase();
    }

    this.refresh();

    fromEvent(this.filter.nativeElement, 'keyup').pipe(
      debounceTime(150), // Keep low -- Client-side filter
      map(() => this.filter.nativeElement.value.trim()), // Detect 'distinct' on value not on KeyEvent
      distinctUntilChanged(),
    ).subscribe(value => {
      this.updateURL();
      this.dataSource.filter = value.toLowerCase();
    });

    this.dataSource.sort = this.sort;
  }

  refresh() {
    this.yamcs.yamcsClient.getEndpoints().then(page => {
      this.dataSource.data = page.endpoints || [];
    });
  }

  selectEndpoint(endpoint: Endpoint) {
    this.selectedEndpoint$.next(endpoint);
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
