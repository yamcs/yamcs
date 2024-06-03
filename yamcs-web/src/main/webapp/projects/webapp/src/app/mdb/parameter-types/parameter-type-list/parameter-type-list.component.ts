import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, ViewChild } from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { MatPaginator } from '@angular/material/paginator';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { GetParameterTypesOptions, MessageService, ParameterType, WebappSdkModule, YaColumnChooser, YaColumnInfo, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { ParameterTypesDataSource } from './parameter-types.datasource';

@Component({
  standalone: true,
  templateUrl: './parameter-type-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class ParameterTypesComponent implements AfterViewInit {

  shortName = false;
  pageSize = 100;

  @ViewChild('top', { static: true })
  top: ElementRef;

  @ViewChild(MatPaginator, { static: true })
  paginator: MatPaginator;

  @ViewChild(YaColumnChooser)
  columnChooser: YaColumnChooser;

  filterControl = new UntypedFormControl();

  dataSource: ParameterTypesDataSource;

  columns: YaColumnInfo[] = [
    { id: 'name', label: 'Name', alwaysVisible: true },
    { id: 'type', label: 'Type', visible: true },
    { id: 'units', label: 'Units', visible: true },
    { id: 'shortDescription', label: 'Description' },
    { id: 'actions', label: '', alwaysVisible: true },
  ];

  // Added dynamically based on actual parameter types.
  aliasColumns$ = new BehaviorSubject<YaColumnInfo[]>([]);

  selection = new SelectionModel<ParameterType>(false);

  constructor(
    readonly yamcs: YamcsService,
    title: Title,
    private route: ActivatedRoute,
    private router: Router,
    private messageService: MessageService,
  ) {
    title.setTitle('Parameter types');
    this.dataSource = new ParameterTypesDataSource(yamcs);
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
    const options: GetParameterTypesOptions = {
      pos: this.paginator.pageIndex * this.pageSize,
      limit: this.pageSize,
      fields: ['name', 'qualifiedName', 'engType', 'unitSet', 'alias', 'shortDescription'],
    };
    const filterValue = this.filterControl.value;
    if (filterValue) {
      options.q = filterValue.toLowerCase();
    }
    this.dataSource.loadParameterTypes(options).then(() => {
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
    const filterValue = this.filterControl.value;
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        page: this.paginator.pageIndex || null,
        filter: filterValue || null,
      },
      queryParamsHandling: 'merge',
    });
  }

  selectNext() {
    const items = this.dataSource.parameterTypes$.value;
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
    const items = this.dataSource.parameterTypes$.value;
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
      const items = this.dataSource.parameterTypes$.value;
      if (items.indexOf(item) !== -1) {
        this.router.navigate(['/mdb/parameter-types', item.qualifiedName], {
          queryParams: { c: this.yamcs.context }
        });
      }
    }
  }
}
