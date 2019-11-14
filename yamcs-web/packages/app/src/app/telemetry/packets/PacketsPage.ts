import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { Instance, TmStatistics } from '@yamcs/client';
import { Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './PacketsPage.html',
  styleUrls: ['./PacketsPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PacketsPage implements AfterViewInit, OnDestroy {

  filterControl = new FormControl();

  instance: Instance;

  tmstatsSubscription: Subscription;

  @ViewChild(MatSort, { static: false })
  sort: MatSort;

  dataSource = new MatTableDataSource<TmStatistics>();

  displayedColumns = [
    'packetName',
    'lastPacketTime',
    'lastReceived',
    'packetRate',
    'dataRate',
  ];

  constructor(
    yamcs: YamcsService,
    title: Title,
    private route: ActivatedRoute,
    private router: Router,
  ) {
    title.setTitle('Packets');
    const processor = yamcs.getProcessor();
    this.instance = yamcs.getInstance();
    yamcs.getInstanceClient()!.getProcessorStatistics().then(response => {
      this.tmstatsSubscription = response.statistics$.pipe(
        filter(stats => stats.processor === processor.name),
        map(stats => stats.tmstats || []),
      ).subscribe(tmstats => {
        this.dataSource.data = tmstats || [];
      });
    });
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
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

  ngOnDestroy() {
    if (this.tmstatsSubscription) {
      this.tmstatsSubscription.unsubscribe();
    }
  }
}
