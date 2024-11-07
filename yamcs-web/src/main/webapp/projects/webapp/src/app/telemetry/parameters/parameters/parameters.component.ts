import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup } from '@angular/forms';
import { MatPaginator } from '@angular/material/paginator';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { GetParametersOptions, Synchronizer, WebappSdkModule, YaColumnChooser, YaColumnInfo, YaSelectOption, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { ListItem, ParametersDataSource } from './parameters.datasource';

export const PLIST_COLUMNS: YaColumnInfo[] = [
  { id: 'name', label: 'Name', alwaysVisible: true },
  { id: 'type', label: 'Type', visible: true },
  { id: 'dataSource', label: 'Data source', visible: true },
  { id: 'engValue', label: 'Value', visible: true },
  { id: 'gentime', label: 'Generation time', visible: false },
  { id: 'rectime', label: 'Reception time', visible: false },
  { id: 'shortDescription', label: 'Description', visible: true },
  { id: 'actions', label: '', alwaysVisible: true },
];

export const PLIST_TYPE_OPTIONS: YaSelectOption[] = [
  { id: 'ANY', label: 'Any type' },
  { id: 'aggregate', label: 'aggregate' },
  { id: 'array', label: 'array' },
  { id: 'binary', label: 'binary' },
  { id: 'boolean', label: 'boolean' },
  { id: 'enumeration', label: 'enumeration' },
  { id: 'float', label: 'float' },
  { id: 'integer', label: 'integer' },
  { id: 'string', label: 'string' },
  { id: 'time', label: 'time' },
];

export const PLIST_SOURCE_OPTIONS: YaSelectOption[] = [
  { id: 'ANY', label: 'Any source' },
  { id: 'COMMAND', label: 'Command' },
  { id: 'COMMAND_HISTORY', label: 'Command History' },
  { id: 'CONSTANT', label: 'Constant' },
  { id: 'DERIVED', label: 'Derived' },
  { id: 'EXTERNAL1', label: 'External 1' },
  { id: 'EXTERNAL2', label: 'External 2' },
  { id: 'EXTERNAL3', label: 'External 3' },
  { id: 'GROUND', label: 'Ground' },
  { id: 'LOCAL', label: 'Local' },
  { id: 'SYSTEM', label: 'System' },
  { id: 'TELEMETERED', label: 'Telemetered' },
];

@Component({
  standalone: true,
  templateUrl: './parameters.component.html',
  styleUrl: './parameters.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class ParametersComponent implements AfterViewInit, OnDestroy {

  filterForm = new UntypedFormGroup({
    filter: new UntypedFormControl(),
    type: new UntypedFormControl('ANY'),
    source: new UntypedFormControl('ANY'),
  });

  shortName = false;
  pageSize = 100;

  // For use in this controller (immediately updated)
  private system: string | null = null;

  // For use in the template (update only when the data has arrived)
  system$ = new BehaviorSubject<string | null>(null);

  breadcrumb$ = new BehaviorSubject<BreadCrumbItem[]>([]);

  @ViewChild('top', { static: true })
  top: ElementRef;

  @ViewChild(MatPaginator, { static: true })
  paginator: MatPaginator;

  @ViewChild(YaColumnChooser)
  columnChooser: YaColumnChooser;

  dataSource: ParametersDataSource;

  columns = PLIST_COLUMNS;
  typeOptions = PLIST_TYPE_OPTIONS;
  sourceOptions = PLIST_SOURCE_OPTIONS;

  // Added dynamically based on actual commands.
  aliasColumns$ = new BehaviorSubject<YaColumnInfo[]>([]);

  private queryParamMapSubscription: Subscription;

  selection = new SelectionModel<ListItem>(false);

  // Would prefer to use formGroup, but when using valueChanges this
  // only is updated after the callback...
  private type: string;
  private source: string;
  private filter: string;

  constructor(
    readonly yamcs: YamcsService,
    private route: ActivatedRoute,
    private router: Router,
    private synchronizer: Synchronizer,
    changeDetection: ChangeDetectorRef,
  ) {
    this.dataSource = new ParametersDataSource(this.yamcs, this.synchronizer, changeDetection);
  }

  ngAfterViewInit() {
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.has('filter')) {
      this.filter = queryParams.get('filter') || '';
      this.filterForm.get('filter')!.setValue(this.filter);
    }
    if (queryParams.has('type')) {
      this.type = queryParams.get('type')!;
      this.filterForm.get('type')!.setValue(this.type);
    }
    if (queryParams.has('source')) {
      this.source = queryParams.get('source')!;
      this.filterForm.get('source')!.setValue(this.source);
    }

    this.filterForm.get('filter')!.valueChanges.subscribe(filter => {
      this.paginator.pageIndex = 0;
      this.filter = filter;
      this.updateDataSource();
    });

    this.filterForm.get('type')!.valueChanges.forEach(type => {
      this.type = (type !== 'ANY') ? type : null;
      this.updateDataSource();
    });

    this.filterForm.get('source')!.valueChanges.forEach(source => {
      this.source = (source !== 'ANY') ? source : null;
      this.updateDataSource();
    });

    this.changeSystem(this.route.snapshot.queryParamMap);
    this.queryParamMapSubscription = this.route.queryParamMap.subscribe(map => {
      if (map.get('system') !== this.system) {
        this.changeSystem(map);
      }
    });

    this.paginator.page.subscribe(() => {
      this.updateDataSource();
      this.top.nativeElement.scrollIntoView();
    });
  }

  changeSystem(map: ParamMap) {
    this.system = map.get('system');
    this.updateBrowsePath();

    if (map.has('page')) {
      this.paginator.pageIndex = Number(map.get('page'));
    } else {
      this.paginator.pageIndex = 0;
    }

    this.updateDataSource();
  }

  private updateDataSource() {
    this.updateURL();
    const options: GetParametersOptions = {
      system: this.system || '/',
      details: true,
      pos: this.paginator.pageIndex * this.pageSize,
      limit: this.pageSize,
    };
    if (this.filter) {
      options.q = this.filter;
      options.searchMembers = true;
    }
    if (this.type) {
      options.type = this.type;
    }
    if (this.source) {
      options.source = this.source;
    }
    this.dataSource.loadParameters(options).then(() => {
      this.selection.clear();
      this.updateBrowsePath();
      this.system$.next(this.system);

      // Reset alias columns
      const newColumns = [...this.columns];
      for (const aliasColumn of this.aliasColumns$.value) {
        const idx = newColumns.indexOf(aliasColumn);
        if (idx !== -1) {
          newColumns.splice(idx, 1);
        }
      }
      const aliasColumns = [];
      for (const namespace of this.dataSource.getAliasNamespaces()) {
        const aliasColumn = { id: namespace, label: namespace, alwaysVisible: true };
        aliasColumns.push(aliasColumn);
      }
      newColumns.splice(1, 0, ...aliasColumns); // Insert after name column
      this.aliasColumns$.next(aliasColumns);
      this.columnChooser.recalculate(newColumns);
    });
  }

  private updateURL() {
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        page: this.paginator.pageIndex || null,
        filter: this.filter || null,
        type: this.type || null,
        source: this.source || null,
        system: this.system || null,
      },
      queryParamsHandling: 'merge',
    });
  }

  private updateBrowsePath() {
    const breadcrumb: BreadCrumbItem[] = [];
    let path = '';
    if (this.system) {
      for (const part of this.system.slice(1).split('/')) {
        path += '/' + part;
        breadcrumb.push({
          name: part,
          route: '/telemetry/parameters',
          queryParams: { system: path, c: this.yamcs.context },
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
      if (item.parameter && items.indexOf(item) !== -1) {
        this.router.navigate(['/telemetry/parameters' + item.parameter?.qualifiedName], {
          queryParams: { c: this.yamcs.context }
        });
      }
    }
  }

  ngOnDestroy() {
    this.queryParamMapSubscription?.unsubscribe();
    this.dataSource.disconnect();
  }
}

export interface BreadCrumbItem {
  name: string;
  route: string;
  queryParams: any;
}
