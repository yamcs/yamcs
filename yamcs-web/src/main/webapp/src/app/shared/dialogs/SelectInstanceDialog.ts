import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { MatPaginator } from '@angular/material/paginator';
import { MatTableDataSource } from '@angular/material/table';
import { Instance } from '../../client';
import { AuthService } from '../../core/services/AuthService';
import { ConfigService } from '../../core/services/ConfigService';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-select-instance-dialog',
  templateUrl: './SelectInstanceDialog.html',
  styleUrls: ['./SelectInstanceDialog.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SelectInstanceDialog implements AfterViewInit {

  filterControl = new FormControl();

  @ViewChild(MatPaginator, { static: true })
  paginator: MatPaginator;

  dataSource = new MatTableDataSource<Instance>([]);
  selection = new SelectionModel<Instance>();

  displayedColumns = [
    'selected',
    'name',
    'processor',
  ];

  constructor(
    private dialogRef: MatDialogRef<SelectInstanceDialog>,
    private authService: AuthService,
    readonly yamcs: YamcsService,
    private config: ConfigService,
  ) {
    this.dataSource.filterPredicate = (instance, filter) => {
      return instance.name.toLowerCase().indexOf(filter) >= 0;
    };

    yamcs.yamcsClient.getInstances({
      filter: 'state=running',
    }).then(instances => {
      this.dataSource.data = instances;
    });
  }

  isCreateInstanceEnabled() {
    const user = this.authService.getUser()!;
    return this.config.hasTemplates() && user.hasSystemPrivilege('CreateInstances');
  }

  ngAfterViewInit() {
    this.filterControl.valueChanges.subscribe(() => {
      const value = this.filterControl.value || '';
      this.dataSource.filter = value.toLowerCase();
    });

    this.dataSource.paginator = this.paginator;
  }

  applySelection() {
    const selected = this.selection.selected;
    if (selected.length) {
      const selectedInstance = this.selection.selected[0];
      this.dialogRef.close();
      if (this.yamcs.instance !== selectedInstance.name) {
        this.yamcs.switchContext(selectedInstance.name);
      }
    }
  }
}
