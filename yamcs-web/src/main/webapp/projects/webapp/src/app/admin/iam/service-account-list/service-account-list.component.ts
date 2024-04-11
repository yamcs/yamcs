import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { MessageService, ServiceAccount, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { AdminPageTemplateComponent } from '../../shared/admin-page-template/admin-page-template.component';
import { AdminToolbarComponent } from '../../shared/admin-toolbar/admin-toolbar.component';

@Component({
  standalone: true,
  templateUrl: './service-account-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdminPageTemplateComponent,
    AdminToolbarComponent,
    WebappSdkModule,
  ],
})
export class ServiceAccountListComponent implements AfterViewInit {

  filterControl = new UntypedFormControl();

  @ViewChild(MatSort, { static: true })
  sort: MatSort;

  displayedColumns = [
    'name',
    'actions',
  ];
  dataSource = new MatTableDataSource<ServiceAccount>();

  constructor(
    private yamcs: YamcsService,
    title: Title,
    private route: ActivatedRoute,
    private router: Router,
    private messageService: MessageService,
  ) {
    title.setTitle('Service accounts');
    this.dataSource.filterPredicate = (serviceAccount, filter) => {
      return serviceAccount.name.toLowerCase().indexOf(filter) >= 0;
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
    this.yamcs.yamcsClient.getServiceAccounts().then(page => {
      this.dataSource.data = page.serviceAccounts || [];
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

  deleteServiceAccount(name: string) {
    if (confirm(`Are you sure you want to delete service account ${name}`)) {
      this.yamcs.yamcsClient.deleteServiceAccount(name)
        .then(() => this.refresh())
        .catch(err => this.messageService.showError(err));
    }
  }
}
