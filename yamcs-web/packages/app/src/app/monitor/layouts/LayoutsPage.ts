import { SelectionModel } from '@angular/cdk/collections';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { Instance, ObjectInfo } from '@yamcs/client';
import { AuthService } from '../../core/services/AuthService';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './LayoutsPage.html',
  styleUrls: ['./LayoutsPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LayoutsPage {

  instance: Instance;

  displayedColumns = ['select', 'name', 'visibility'];
  dataSource = new MatTableDataSource<ObjectInfo>([]);
  selection = new SelectionModel<ObjectInfo>(true, []);

  constructor(title: Title, yamcs: YamcsService, authService: AuthService) {
    title.setTitle('Layouts - Yamcs');
    this.instance = yamcs.getInstance();

    const username = authService.getUser()!.getUsername();
    yamcs.getInstanceClient()!.listObjects(`user.${username}`, {
      prefix: 'layouts',
    }).then(objects => {
      this.dataSource.data = objects;
    });
  }

  isAllSelected() {
    const numSelected = this.selection.selected.length;
    const numRows = this.dataSource.data.length;
    return numSelected === numRows;
  }

  masterToggle() {
    this.isAllSelected() ?
        this.selection.clear() :
        this.dataSource.data.forEach(row => this.selection.select(row));
  }

  toggleOne(row: ObjectInfo) {
    this.selection.clear();
    this.selection.toggle(row);
  }
}
