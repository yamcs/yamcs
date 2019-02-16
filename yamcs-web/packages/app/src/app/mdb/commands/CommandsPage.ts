import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { MatPaginator } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { GetCommandsOptions, Instance } from '@yamcs/client';
import { fromEvent } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { YamcsService } from '../../core/services/YamcsService';
import { ColumnInfo } from '../../shared/template/ColumnChooser';
import { CommandsDataSource } from './CommandsDataSource';

@Component({
  templateUrl: './CommandsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandsPage implements OnInit, AfterViewInit {

  instance: Instance;
  shortName = false;
  pageSize = 100;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  @ViewChild('filter')
  filter: ElementRef;

  dataSource: CommandsDataSource;

  columns: ColumnInfo[] = [
    { id: 'name', label: 'Name', alwaysVisible: true },
    { id: 'abstract', label: 'Abstract' },
    { id: 'shortDescription', label: 'Description' },
  ];

  displayedColumns = [
    'name',
    'abstract',
  ];

  constructor(yamcs: YamcsService, title: Title, private preferenceStore: PreferenceStore) {
    title.setTitle('Commands - Yamcs');
    this.instance = yamcs.getInstance();
    const cols = preferenceStore.getVisibleColumns('commands');
    if (cols.length) {
      this.displayedColumns = cols;
    }
    this.dataSource = new CommandsDataSource(yamcs);
  }

  ngOnInit() {
    this.dataSource.loadCommands({
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
    const options: GetCommandsOptions = {
      pos: this.paginator.pageIndex * this.pageSize,
      limit: this.pageSize,
    };
    const filterValue = this.filter.nativeElement.value.trim().toLowerCase();
    if (filterValue) {
      options.q = filterValue;
    }
    this.dataSource.loadCommands(options);
  }

  updateColumns(displayedColumns: string[]) {
    this.displayedColumns = displayedColumns;
    this.preferenceStore.setVisibleColumns('commands', displayedColumns);
  }
}
