import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatPaginator } from '@angular/material/paginator';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { GetCommandsOptions, Instance } from '@yamcs/client';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { YamcsService } from '../../core/services/YamcsService';
import { ColumnInfo } from '../../shared/template/ColumnChooser';
import { CommandsDataSource } from './CommandsDataSource';

@Component({
  templateUrl: './CommandsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandsPage implements AfterViewInit {

  instance: Instance;
  shortName = false;
  pageSize = 100;

  @ViewChild('top', { static: true })
  top: ElementRef;

  @ViewChild(MatPaginator, { static: true })
  paginator: MatPaginator;

  filterControl = new FormControl();

  dataSource: CommandsDataSource;

  columns: ColumnInfo[] = [
    { id: 'name', label: 'Name', alwaysVisible: true },
    { id: 'significance', label: 'Significance' },
    { id: 'abstract', label: 'Abstract' },
    { id: 'shortDescription', label: 'Description' },
  ];

  displayedColumns = [
    'name',
    'significance',
    'abstract',
  ];

  constructor(
    yamcs: YamcsService,
    title: Title,
    private preferenceStore: PreferenceStore,
    private route: ActivatedRoute,
    private router: Router,
  ) {
    title.setTitle('Commands');
    this.instance = yamcs.getInstance();
    const cols = preferenceStore.getVisibleColumns('commands');
    if (cols && cols.length) {
      this.displayedColumns = cols;
    }
    this.dataSource = new CommandsDataSource(yamcs);
  }

  ngAfterViewInit() {
    const queryParams = this.route.snapshot.queryParamMap;
    this.filterControl.setValue(queryParams.get('filter'));

    this.filterControl.valueChanges.subscribe(() => {
      this.paginator.pageIndex = 0;
      this.updateDataSource();
    });

    if (queryParams.has('page')) {
      this.paginator.pageIndex = Number(queryParams.get('page'));
    }
    this.updateDataSource();
    this.paginator.page.subscribe(() => {
      this.updateDataSource();
      this.top.nativeElement.scrollIntoView();
    });
  }

  private updateDataSource() {
    this.updateURL();
    const options: GetCommandsOptions = {
      pos: this.paginator.pageIndex * this.pageSize,
      limit: this.pageSize,
    };
    const filterValue = this.filterControl.value;
    if (filterValue) {
      options.q = filterValue.toLowerCase();
    }
    this.dataSource.loadCommands(options);
  }

  private updateURL() {
    const filterValue = this.filterControl.value;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        page: this.paginator.pageIndex || null,
        filter: filterValue || null,
      },
      queryParamsHandling: 'merge',
    });
  }

  updateColumns(displayedColumns: string[]) {
    this.displayedColumns = displayedColumns;
    this.preferenceStore.setVisibleColumns('commands', displayedColumns);
  }
}
