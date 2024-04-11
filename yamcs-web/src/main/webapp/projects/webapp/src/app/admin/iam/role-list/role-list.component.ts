import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { RoleInfo, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { AdminPageTemplateComponent } from '../../shared/admin-page-template/admin-page-template.component';
import { AdminToolbarComponent } from '../../shared/admin-toolbar/admin-toolbar.component';

@Component({
  standalone: true,
  templateUrl: './role-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdminPageTemplateComponent,
    AdminToolbarComponent,
    WebappSdkModule,
  ],
})
export class RoleListComponent implements AfterViewInit {

  filterControl = new UntypedFormControl();

  @ViewChild(MatSort, { static: true })
  sort: MatSort;

  displayedColumns = [
    'name',
    'default',
    'actions',
  ];
  dataSource = new MatTableDataSource<RoleInfo>();

  constructor(
    private yamcs: YamcsService,
    title: Title,
    private route: ActivatedRoute,
    private router: Router,
  ) {
    title.setTitle('Roles');
    this.dataSource.filterPredicate = (role, filter) => {
      return role.name.toLowerCase().indexOf(filter) >= 0;
    };
  }

  ngAfterViewInit() {
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.has('filter')) {
      this.filterControl.setValue(queryParams.get('filter'));
      this.dataSource.filter = queryParams.get('filter')!.toLowerCase();
    }

    this.filterControl.valueChanges.subscribe(() => {
      this.updateURL();
      const value = this.filterControl.value || '';
      this.dataSource.filter = value.toLowerCase();
    });


    this.refresh();
    this.dataSource.sort = this.sort;
  }

  private refresh() {
    this.yamcs.yamcsClient.getRoles().then(roles => {
      this.dataSource.data = roles;
    });
  }

  private updateURL() {
    const filterValue = this.filterControl.value;
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        filter: filterValue || null,
      },
      queryParamsHandling: 'merge',
    });
  }
}
