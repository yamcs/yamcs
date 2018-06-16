import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { Instance, ObjectInfo } from '@yamcs/client';
import { AuthService } from '../../core/services/AuthService';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './LayoutsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LayoutsPage {

  instance: Instance;

  displayedColumns = ['name'];
  dataSource = new MatTableDataSource<ObjectInfo>([]);

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
}
