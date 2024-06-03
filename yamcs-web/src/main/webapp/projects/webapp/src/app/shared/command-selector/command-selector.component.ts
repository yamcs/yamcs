import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, forwardRef, Input, ViewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, UntypedFormControl } from '@angular/forms';
import { MatPaginator } from '@angular/material/paginator';
import { Command, GetCommandsOptions, SpaceSystem, WebappSdkModule, YaColumnChooser, YaColumnInfo, YamcsService, YaSearchFilter } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { CommandsDataSource } from '../../commanding/command-sender/send-command/commands.datasource';
import { SignificanceLevelComponent } from '../significance-level/significance-level.component';

@Component({
  standalone: true,
  selector: 'app-command-selector',
  templateUrl: './command-selector.component.html',
  styleUrl: './command-selector.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => CommandSelectorComponent),
    multi: true
  }],
  imports: [
    SignificanceLevelComponent,
    WebappSdkModule,
  ],
})
export class CommandSelectorComponent implements ControlValueAccessor, AfterViewInit {

  @Input()
  path: string;

  pageSize = 100;

  system: string | null = null;
  breadcrumb$ = new BehaviorSubject<BreadCrumbItem[]>([]);

  @ViewChild('top', { static: true })
  top: ElementRef;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  @ViewChild(YaColumnChooser)
  columnChooser: YaColumnChooser;

  @ViewChild('searchFilter')
  searchFilter: YaSearchFilter;

  filterControl = new UntypedFormControl();

  dataSource: CommandsDataSource;

  columns: YaColumnInfo[] = [
    { id: 'name', label: 'Name', alwaysVisible: true },
    { id: 'significance', label: 'Significance', visible: true },
    { id: 'shortDescription', label: 'Description' },
    { id: 'actions', label: '', alwaysVisible: true },
  ];

  // Added dynamically based on actual commands.
  aliasColumns$ = new BehaviorSubject<YaColumnInfo[]>([]);

  selection = new SelectionModel<ListItem>(false);
  selectedCommand$ = new BehaviorSubject<ListItem | null>(null);

  private onChange = (_: Command | null) => { };
  private onTouched = () => { };

  constructor(readonly yamcs: YamcsService, private changeDetection: ChangeDetectorRef) {
    this.dataSource = new CommandsDataSource(yamcs);
    this.selectedCommand$.subscribe(async item => {
      if (item && item.command) {
        const commandDetail = await this.yamcs.yamcsClient.getCommand(this.yamcs.instance!, item.command.qualifiedName);
        return this.onChange(commandDetail);
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
      details: true,
      pos: this.paginator.pageIndex * this.pageSize,
      limit: this.pageSize,
      fields: ['name', 'qualifiedName', 'alias', 'effectiveSignificance', 'shortDescription'],
    };
    const filterValue = this.filterControl.value;
    if (filterValue) {
      options.q = filterValue.toLowerCase();
    }
    this.dataSource.loadCommands(options).then(() => {
      this.selection.clear();
      this.updateBrowsePath();

      // Reset alias columns
      for (const aliasColumn of this.aliasColumns$.value) {
        const idx = this.columns.indexOf(aliasColumn);
        if (idx !== -1) {
          this.columns.splice(idx, 1);
        }
      }
      const aliasColumns = [];
      for (const namespace of this.dataSource.getAliasNamespaces()) {
        const aliasColumn = { id: namespace, label: namespace, alwaysVisible: true };
        aliasColumns.push(aliasColumn);
      }
      this.columns.splice(1, 0, ...aliasColumns); // Insert after name column
      this.aliasColumns$.next(aliasColumns);
      this.columnChooser.recalculate(this.columns);
    });
  }

  selectRow(row: ListItem) {
    if (row.system) {
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

  selectNext() {
    const items = this.dataSource.items$.value;
    let idx = 0;
    if (this.selection.hasValue()) {
      const currentItem = this.selection.selected[0];
      if (items.indexOf(currentItem) !== -1) {
        idx = Math.min(items.indexOf(currentItem) + 1, items.length - 1);
      }
    }
    this.selection.select(items[idx]);
  }

  selectPrevious() {
    const items = this.dataSource.items$.value;
    let idx = 0;
    if (this.selection.hasValue()) {
      const currentItem = this.selection.selected[0];
      if (items.indexOf(currentItem) !== -1) {
        idx = Math.max(items.indexOf(currentItem) - 1, 0);
      }
    }
    this.selection.select(items[idx]);
  }

  applySelection() {
    if (this.selection.hasValue()) {
      const item = this.selection.selected[0];
      const items = this.dataSource.items$.value;
      if (item.command && items.indexOf(item) !== -1) {
        this.selectRow(item);
      }
    }
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
  name: string;
  system?: SpaceSystem;
  command?: Command;
}

export interface BreadCrumbItem {
  name: string;
  system: string;
}
