import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { MatPaginator } from '@angular/material/paginator';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { ConnectionInfo, GetCommandsOptions, WebappSdkModule, YaColumnChooser, YaColumnInfo, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { SignificanceLevelComponent } from '../../../shared/significance-level/significance-level.component';
import { SendCommandWizardStepComponent } from '../send-command-wizard-step/send-command-wizard-step.component';
import { CommandsDataSource, ListItem } from './commands.datasource';

@Component({
  standalone: true,
  templateUrl: './send-command.component.html',
  styleUrl: './send-command.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    SendCommandWizardStepComponent,
    WebappSdkModule,
    SignificanceLevelComponent,
  ],
})
export class SendCommandComponent implements AfterViewInit, OnDestroy {

  connectionInfo$: Observable<ConnectionInfo | null>;

  pageSize = 100;

  system: string | null = null;
  breadcrumb$ = new BehaviorSubject<BreadCrumbItem[]>([]);

  @ViewChild('top', { static: true })
  top: ElementRef;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  @ViewChild(YaColumnChooser)
  columnChooser: YaColumnChooser;

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

  private queryParamMapSubscription: Subscription;

  selection = new SelectionModel<ListItem>(false);

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
    private route: ActivatedRoute,
    private router: Router,
  ) {
    title.setTitle('Send a command');
    this.connectionInfo$ = yamcs.connectionInfo$;
    this.dataSource = new CommandsDataSource(yamcs);
  }

  ngAfterViewInit() {
    this.filterControl.setValue(this.route.snapshot.queryParamMap.get('filter'));
    this.changeSystem(this.route.snapshot.queryParamMap);

    this.queryParamMapSubscription = this.route.queryParamMap.subscribe(map => {
      if (map.get('system') !== this.system) {
        this.changeSystem(map);
      }
    });
    this.filterControl.valueChanges.subscribe(() => {
      this.paginator.pageIndex = 0;
      this.updateDataSource();
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

  private updateURL() {
    const filterValue = this.filterControl.value;
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        page: this.paginator.pageIndex || null,
        filter: filterValue || null,
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
          route: '/commanding/send',
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
      if (item.command && items.indexOf(item) !== -1) {
        this.router.navigate(['/commanding/send' + item.command?.qualifiedName], {
          queryParams: { c: this.yamcs.context }
        });
      }
    }
  }

  ngOnDestroy() {
    this.queryParamMapSubscription?.unsubscribe();
  }
}

export interface BreadCrumbItem {
  name?: string;
  route: string;
  queryParams: any;
}
