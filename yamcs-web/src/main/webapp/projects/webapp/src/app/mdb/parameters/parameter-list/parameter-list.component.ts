import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, ViewChild } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup } from '@angular/forms';
import { MatPaginator } from '@angular/material/paginator';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { GetParametersOptions, MessageService, Parameter, WebappSdkModule, YaColumnChooser, YaColumnInfo, YaSelectOption, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { ParametersDataSource } from './parameters.datasource';

@Component({
  standalone: true,
  templateUrl: './parameter-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class ParameterListComponent implements AfterViewInit {

  filterForm = new UntypedFormGroup({
    filter: new UntypedFormControl(),
    type: new UntypedFormControl('ANY'),
    source: new UntypedFormControl('ANY'),
  });

  pageSize = 100;

  @ViewChild('top', { static: true })
  top: ElementRef;

  @ViewChild(MatPaginator, { static: true })
  paginator: MatPaginator;

  @ViewChild(YaColumnChooser)
  columnChooser: YaColumnChooser;

  dataSource: ParametersDataSource;

  columns: YaColumnInfo[] = [
    { id: 'name', label: 'Name', alwaysVisible: true },
    { id: 'type', label: 'Type', visible: true },
    { id: 'units', label: 'Units', visible: true },
    { id: 'dataSource', label: 'Data source', visible: true },
    { id: 'shortDescription', label: 'Description' },
    { id: 'actions', label: '', alwaysVisible: true },
  ];

  // Added dynamically based on actual parameters.
  aliasColumns$ = new BehaviorSubject<YaColumnInfo[]>([]);

  typeOptions: YaSelectOption[] = [
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

  sourceOptions: YaSelectOption[] = [
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

  selection = new SelectionModel<Parameter>(false);

  // Would prefer to use formGroup, but when using valueChanges this
  // only is updated after the callback...
  private type: string;
  private source: string;
  private filter: string;

  constructor(
    readonly yamcs: YamcsService,
    title: Title,
    private route: ActivatedRoute,
    private router: Router,
    private messageService: MessageService,
  ) {
    title.setTitle('Parameters');
    this.dataSource = new ParametersDataSource(yamcs);
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
    const options: GetParametersOptions = {
      pos: this.paginator.pageIndex * this.pageSize,
      limit: this.pageSize,
      details: true,
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
    }).catch(err => this.messageService.showError(err));
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
      },
      queryParamsHandling: 'merge',
    });
  }

  selectNext() {
    const items = this.dataSource.parameters$.value;
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
    const items = this.dataSource.parameters$.value;
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
      const items = this.dataSource.parameters$.value;
      if (items.indexOf(item) !== -1) {
        this.router.navigate(['/mdb/parameters', item.qualifiedName], {
          queryParams: { c: this.yamcs.context }
        });
      }
    }
  }
}
