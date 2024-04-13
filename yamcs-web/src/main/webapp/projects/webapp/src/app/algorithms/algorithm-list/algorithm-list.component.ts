import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup } from '@angular/forms';
import { MatPaginator } from '@angular/material/paginator';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { GetAlgorithmsOptions, WebappSdkModule, YaSelectOption, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';
import { AlgorithmsDataSource, ListItem } from './algorithms.datasource';

@Component({
  standalone: true,
  templateUrl: './algorithm-list.component.html',
  styleUrl: './algorithm-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class AlgorithmListComponent implements AfterViewInit, OnDestroy {

  filterForm = new UntypedFormGroup({
    filter: new UntypedFormControl(),
    scope: new UntypedFormControl('ANY'),
  });

  shortName = false;
  pageSize = 100;

  system: string | null = null;
  breadcrumb$ = new BehaviorSubject<BreadCrumbItem[]>([]);

  @ViewChild('top', { static: true })
  top: ElementRef;

  @ViewChild(MatPaginator, { static: true })
  paginator: MatPaginator;

  dataSource: AlgorithmsDataSource;

  displayedColumns = [
    'name',
    'type',
    'language',
    'scope',
    'shortDescription',
    'actions',
  ];

  scopeOptions: YaSelectOption[] = [
    { id: 'ANY', label: 'Any scope' },
    { id: 'GLOBAL', label: 'Global' },
    { id: 'COMMAND_VERIFICATION', label: 'Command Verification' },
    { id: 'CONTAINER_PROCESSING', label: 'Container Processing' },
  ];

  private queryParamMapSubscription: Subscription;

  selection = new SelectionModel<ListItem>(false);

  // Would prefer to use formGroup, but when using valueChanges this
  // only is updated after the callback...
  private scope: string;
  private filter: string;

  constructor(
    readonly yamcs: YamcsService,
    title: Title,
    private route: ActivatedRoute,
    private router: Router,
  ) {
    title.setTitle('Algorithms');
    this.dataSource = new AlgorithmsDataSource(yamcs);
  }

  ngAfterViewInit() {
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.has('filter')) {
      this.filter = queryParams.get('filter') || '';
      this.filterForm.get('filter')!.setValue(this.filter);
    }
    if (queryParams.has('scope')) {
      this.scope = queryParams.get('scope')!;
      this.filterForm.get('scope')!.setValue(this.scope);
    }

    this.filterForm.get('filter')!.valueChanges.subscribe(filter => {
      this.paginator.pageIndex = 0;
      this.filter = filter;
      this.updateDataSource();
    });

    this.filterForm.get('scope')!.valueChanges.forEach(scope => {
      this.scope = (scope !== 'ANY') ? scope : null;
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
    const options: GetAlgorithmsOptions = {
      system: this.system || '/',
      pos: this.paginator.pageIndex * this.pageSize,
      limit: this.pageSize,
    };
    if (this.filter) {
      options.q = this.filter;
    }
    if (this.scope) {
      options.scope = this.scope;
    }
    this.dataSource.loadAlgorithms(options).then(() => {
      this.selection.clear();
      this.updateBrowsePath();
    });
  }

  private updateURL() {
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        page: this.paginator.pageIndex || null,
        filter: this.filter || null,
        scope: this.scope || null,
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
          route: '/algorithms',
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
      if (items.indexOf(item) !== -1) {
        this.router.navigate(['/algorithms' + item.algorithm?.qualifiedName], {
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
  name?: string;
  route: string;
  queryParams: any;
}
