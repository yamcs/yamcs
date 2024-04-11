import { AfterViewInit, ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { MessageService, ParameterList, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { AuthService } from '../../../core/services/AuthService';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';

@Component({
  standalone: true,
  templateUrl: './parameter-list-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class ParameterListListComponent implements AfterViewInit {

  filterControl = new UntypedFormControl();

  displayedColumns = [
    'name',
    'description',
    'actions',
  ];
  dataSource = new MatTableDataSource<ParameterList>();

  constructor(
    readonly yamcs: YamcsService,
    private authService: AuthService,
    title: Title,
    private route: ActivatedRoute,
    private router: Router,
    private messageService: MessageService,
  ) {
    title.setTitle('Parameter lists');
    this.dataSource.filterPredicate = (plist, filter) => {
      return plist.name.toLowerCase().indexOf(filter) >= 0;
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
  }

  mayManageParameterLists() {
    return this.authService.getUser()!.hasSystemPrivilege('ManageParameterLists');
  }

  private refresh() {
    this.yamcs.yamcsClient.getParameterLists(this.yamcs.instance!).then(plists => {
      this.dataSource.data = plists;
    }).catch(err => this.messageService.showError(err));
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

  deleteList(list: ParameterList) {
    if (confirm(`Are you sure you want to delete the list '${list.name}'`)) {
      this.yamcs.yamcsClient.deleteParameterList(this.yamcs.instance!, list.id)
        .then(() => this.refresh())
        .catch(err => this.messageService.showError(err));
    }
  }
}
