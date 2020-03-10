import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatPaginator } from '@angular/material/paginator';
import { Title } from '@angular/platform-browser';
import { Observable } from 'rxjs';
import { ConnectionInfo, GetCommandsOptions } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { CommandsDataSource } from '../../mdb/commands/CommandsDataSource';

@Component({
  templateUrl: './SendCommandPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SendCommandPage implements AfterViewInit {

  connectionInfo$: Observable<ConnectionInfo | null>;

  instance: string;
  pageSize = 100;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  filterControl = new FormControl();

  dataSource: CommandsDataSource;
  displayedColumns = [
    'name',
    'significance',
    'shortDescription',
  ];

  constructor(
    title: Title,
    yamcs: YamcsService,
  ) {
    title.setTitle('Send a command');
    this.connectionInfo$ = yamcs.connectionInfo$;
    this.instance = yamcs.getInstance();
    this.dataSource = new CommandsDataSource(yamcs);
  }

  ngAfterViewInit() {
    this.filterControl.valueChanges.subscribe(() => {
      this.paginator.pageIndex = 0;
      this.updateDataSource();
    });

    this.updateDataSource();
    this.paginator.page.subscribe(() => {
      this.updateDataSource();
    });
  }

  private updateDataSource() {
    const options: GetCommandsOptions = {
      noAbstract: true,
      pos: this.paginator.pageIndex * this.pageSize,
      limit: this.pageSize,
    };
    const filterValue = this.filterControl.value;
    if (filterValue) {
      options.q = filterValue.toLowerCase();
    }
    this.dataSource.loadCommands(options);
  }
}
