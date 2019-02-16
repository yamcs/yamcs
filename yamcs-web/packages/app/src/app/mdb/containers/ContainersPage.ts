import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { MatPaginator } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { GetContainersOptions, Instance } from '@yamcs/client';
import { fromEvent } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { YamcsService } from '../../core/services/YamcsService';
import { ColumnInfo } from '../../shared/template/ColumnChooser';
import { ContainersDataSource } from './ContainersDataSource';

@Component({
  templateUrl: './ContainersPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContainersPage implements OnInit, AfterViewInit {

  instance: Instance;
  shortName = false;
  pageSize = 100;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  @ViewChild('filter')
  filter: ElementRef;

  dataSource: ContainersDataSource;

  columns: ColumnInfo[] = [
    { id: 'name', label: 'Name', alwaysVisible: true },
    { id: 'maxInterval', label: 'Max Interval' },
    { id: 'sizeInBits', label: 'Size in bits' },
    { id: 'baseContainer', label: 'Base Container' },
    { id: 'restrictionCriteria', label: 'Restriction Criteria' },
    { id: 'shortDescription', label: 'Description' },
  ];

  displayedColumns = [
    'name',
    'maxInterval',
    'sizeInBits',
    'baseContainer',
    'restrictionCriteria',
  ];

  constructor(yamcs: YamcsService, title: Title, private preferenceStore: PreferenceStore) {
    title.setTitle('Containers - Yamcs');
    this.instance = yamcs.getInstance();
    const cols = preferenceStore.getVisibleColumns('containers');
    if (cols.length) {
      this.displayedColumns = cols;
    }
    this.dataSource = new ContainersDataSource(yamcs);
  }


  ngOnInit() {
    this.dataSource.loadContainers({
      limit: this.pageSize,
    });
  }

  ngAfterViewInit() {
    this.paginator.page.subscribe(() => {
      this.updateDataSource();
    });

    fromEvent(this.filter.nativeElement, 'keyup').pipe(
      debounceTime(400),
      distinctUntilChanged(),
    ).subscribe(() => this.updateDataSource());
  }

  updateDataSource() {
    const options: GetContainersOptions = {
      pos: this.paginator.pageIndex * this.pageSize,
      limit: this.pageSize,
    };
    const filterValue = this.filter.nativeElement.value.trim().toLowerCase();
    if (filterValue) {
      options.q = filterValue;
    }
    this.dataSource.loadContainers(options);
  }

  updateColumns(displayedColumns: string[]) {
    this.displayedColumns = displayedColumns;
    this.preferenceStore.setVisibleColumns('containers', displayedColumns);
  }
}
