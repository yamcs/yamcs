import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, ViewChild } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { MatPaginator } from '@angular/material/paginator';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { GetParametersOptions } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { Option } from '../../shared/forms/Select';
import { ColumnInfo } from '../../shared/template/ColumnChooser';
import { ParametersDataSource } from './ParametersDataSource';

@Component({
  templateUrl: './ParametersPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParametersPage implements AfterViewInit {

  filterForm = new FormGroup({
    filter: new FormControl(),
    type: new FormControl('ANY'),
    source: new FormControl('ANY'),
  });

  instance: string;
  shortName = false;
  pageSize = 100;

  @ViewChild('top', { static: true })
  top: ElementRef;

  @ViewChild(MatPaginator, { static: true })
  paginator: MatPaginator;

  dataSource: ParametersDataSource;

  columns: ColumnInfo[] = [
    { id: 'name', label: 'Name', alwaysVisible: true },
    { id: 'type', label: 'Type', visible: true },
    { id: 'units', label: 'Units', visible: true },
    { id: 'dataSource', label: 'Data Source', visible: true },
    { id: 'shortDescription', label: 'Description' },
  ];

  typeOptions: Option[] = [
    { id: 'ANY', label: 'Any type' },
    { id: 'aggregate', label: 'aggregate' },
    { id: 'array', label: 'array' },
    { id: 'binary', label: 'binary' },
    { id: 'boolean', label: 'boolean' },
    { id: 'enumeration', label: 'enumeration' },
    { id: 'float', label: 'float' },
    { id: 'integer', label: 'integer' },
    { id: 'string', label: 'string' },
  ];

  sourceOptions: Option[] = [
    { id: 'ANY', label: 'Any source' },
    { id: 'COMMAND', label: 'Command' },
    { id: 'COMMAND_HISTORY', label: 'Command History' },
    { id: 'CONSTANT', label: 'Constant' },
    { id: 'DERIVED', label: 'Derived' },
    { id: 'EXTERNAL1', label: 'External 1' },
    { id: 'EXTERNAL2', label: 'External 2' },
    { id: 'EXTERNAL3', label: 'External 3' },
    { id: 'LOCAL', label: 'Local' },
    { id: 'SYSTEM', label: 'System' },
    { id: 'TELEMETERED', label: 'Telemetered' },
  ];

  // Would prefer to use formGroup, but when using valueChanges this
  // only is updated after the callback...
  private type: string;
  private source: string;
  private filter: string;

  constructor(
    yamcs: YamcsService,
    title: Title,
    private route: ActivatedRoute,
    private router: Router,
  ) {
    title.setTitle('Parameters');
    this.instance = yamcs.getInstance();
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
    };
    if (this.filter) {
      options.q = this.filter;
    }
    if (this.type) {
      options.type = this.type;
    }
    if (this.source) {
      options.source = this.source;
    }
    this.dataSource.loadParameters(options);
  }

  private updateURL() {
    this.router.navigate([], {
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
}
