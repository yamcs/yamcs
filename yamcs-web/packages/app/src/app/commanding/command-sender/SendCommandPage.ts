import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, ViewChild } from '@angular/core';
import { MatPaginator } from '@angular/material/paginator';
import { Title } from '@angular/platform-browser';
import { GetCommandsOptions, Instance } from '@yamcs/client';
import { fromEvent } from 'rxjs';
import { debounceTime, distinctUntilChanged, map } from 'rxjs/operators';
import { YamcsService } from '../../core/services/YamcsService';
import { CommandsDataSource } from '../../mdb/commands/CommandsDataSource';

@Component({
  templateUrl: './SendCommandPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SendCommandPage implements AfterViewInit {

  instance: Instance;
  pageSize = 100;

  @ViewChild(MatPaginator, { static: false })
  paginator: MatPaginator;

  @ViewChild('filter', { static: false })
  filter: ElementRef;

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
    this.instance = yamcs.getInstance();
    this.dataSource = new CommandsDataSource(yamcs);
  }

  ngAfterViewInit() {
    this.updateDataSource();
    this.paginator.page.subscribe(() => {
      this.updateDataSource();
    });

    fromEvent(this.filter.nativeElement, 'keyup').pipe(
      debounceTime(400),
      map(() => this.filter.nativeElement.value.trim()), // Detect 'distinct' on value not on KeyEvent
      distinctUntilChanged(),
    ).subscribe(() => {
      this.paginator.pageIndex = 0;
      this.updateDataSource();
    });
  }

  private updateDataSource() {
    const options: GetCommandsOptions = {
      noAbstract: true,
      pos: this.paginator.pageIndex * this.pageSize,
      limit: this.pageSize,
    };
    const filterValue = this.filter.nativeElement.value.trim().toLowerCase();
    if (filterValue) {
      options.q = filterValue;
    }
    this.dataSource.loadCommands(options);
  }
}
