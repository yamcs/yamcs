import { Component, ChangeDetectionStrategy, ViewChild, AfterViewInit } from '@angular/core';

import { Instance, SpaceSystem } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';
import { MatTableDataSource, MatSort } from '@angular/material';
import { Title } from '@angular/platform-browser';

@Component({
  templateUrl: './SpaceSystemsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpaceSystemsPage implements AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  displayedColumns = ['qualifiedName'];

  dataSource = new MatTableDataSource<SpaceSystem>();

  instance: Instance;

  constructor(yamcs: YamcsService, title: Title) {
    title.setTitle('Space Systems - Yamcs');
    this.instance = yamcs.getInstance();
    yamcs.getInstanceClient()!.getSpaceSystems().then(spaceSystems => {
      this.dataSource.data = spaceSystems;
    });
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }
}
