import { Component, ChangeDetectionStrategy } from '@angular/core';

import { MatTableDataSource } from '@angular/material';
import { Instance } from '@yamcs/client';
import { NamedLayout, LayoutStorage } from '../displays/LayoutStorage';
import { Title } from '@angular/platform-browser';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './LayoutsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LayoutsPage {

  instance: Instance;

  displayedColumns = ['name'];
  dataSource = new MatTableDataSource<NamedLayout>([]);

  constructor(title: Title, yamcs: YamcsService) {
    title.setTitle('Layouts - Yamcs');
    this.instance = yamcs.getInstance();

    const layouts = LayoutStorage.getLayouts(this.instance.name);
    this.dataSource.data = layouts;
  }
}
