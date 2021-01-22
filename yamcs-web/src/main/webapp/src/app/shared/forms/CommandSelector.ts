import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, forwardRef, Input, ViewChild } from '@angular/core';
import { ControlValueAccessor, FormControl, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MatPaginator } from '@angular/material/paginator';
import { BehaviorSubject } from 'rxjs';
import { Command, GetCommandsOptions } from '../../client';
import { CommandsDataSource } from '../../commanding/command-sender/CommandsDataSource';
import { YamcsService } from '../../core/services/YamcsService';
import { SearchFilter } from './SearchFilter';

@Component({
  selector: 'app-command-selector',
  templateUrl: './CommandSelector.html',
  styleUrls: ['./CommandSelector.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => CommandSelector),
      multi: true
    }
  ]
})
export class CommandSelector implements ControlValueAccessor, AfterViewInit {

  @Input()
  path: string;

  pageSize = 100;

  system: string | null = null;
  breadcrumb$ = new BehaviorSubject<BreadCrumbItem[]>([]);

  @ViewChild('top', { static: true })
  top: ElementRef;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  @ViewChild('searchFilter')
  searchFilter: SearchFilter;

  filterControl = new FormControl();

  dataSource: CommandsDataSource;

  displayedColumns = ['name', 'description', 'significance'];

  selectedCommand$ = new BehaviorSubject<ListItem | null>(null);

  private onChange = (_: Command | null) => { };
  private onTouched = () => { };

  constructor(readonly yamcs: YamcsService, private changeDetection: ChangeDetectorRef) {
    this.dataSource = new CommandsDataSource(yamcs);
    this.selectedCommand$.subscribe(item => {
      if (item && item.command) {
        return this.onChange(item.command);
      } else {
        return this.onChange(null);
      }
    });
  }

  ngAfterViewInit() {
    this.changeSystem('');
    this.searchFilter.filter.nativeElement.focus();
    this.filterControl.valueChanges.subscribe(() => {
      this.paginator.pageIndex = 0;
      this.updateDataSource();
    });
    this.paginator.page.subscribe(() => {
      this.updateDataSource();
      this.top.nativeElement.scrollIntoView();
    });
  }

  changeSystem(system: string, page = 0) {
    this.system = system;
    this.updateBrowsePath();
    this.paginator.pageIndex = page;
    this.updateDataSource();
  }

  private updateDataSource() {
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

  selectRow(row: ListItem) {
    if (row.spaceSystem) {
      this.selectedCommand$.next(null);
      this.changeSystem(row.name);
    } else {
      this.selectedCommand$.next(row);
    }
    return false;
  }

  private updateBrowsePath() {
    const breadcrumb: BreadCrumbItem[] = [];
    let path = '';
    if (this.system) {
      for (const part of this.system.slice(1).split('/')) {
        path += '/' + part;
        breadcrumb.push({
          name: part,
          system: path,
        });
      }
    }
    this.breadcrumb$.next(breadcrumb);
  }

  writeValue(value: any) {
    this.path = value;
  }

  registerOnChange(fn: any) {
    this.onChange = fn;
  }

  registerOnTouched(fn: any) {
    this.onTouched = fn;
  }
}

export class ListItem {
  spaceSystem: boolean;
  name: string;
  command?: Command;
}

export interface BreadCrumbItem {
  name?: string;
  system: string;
}
