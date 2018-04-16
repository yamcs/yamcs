import { Component, ChangeDetectionStrategy } from '@angular/core';

import { SpaceSystem, HistoryInfo } from '@yamcs/client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';
import { MatTableDataSource } from '@angular/material';
import { ColumnInfo } from '../../shared/template/ColumnChooser';

@Component({
  templateUrl: './SpaceSystemChangelogTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpaceSystemChangelogTab {

  qualifiedName: string;

  spaceSystem$: Promise<SpaceSystem>;

  historyDataSource = new MatTableDataSource<HistoryInfo>([]);

  columns: ColumnInfo[] = [
    { id: 'version', label: 'Version' },
    { id: 'date', label: 'Date' },
    { id: 'message', label: 'Message', alwaysVisible: true },
    { id: 'author', label: 'Author' },
  ];

  displayedColumns = [
    'version',
    'date',
    'message',
    'author',
  ];

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    const parent = route.snapshot.parent!;
    this.qualifiedName = parent.paramMap.get('qualifiedName')!;
    this.spaceSystem$ = yamcs.getInstanceClient()!.getSpaceSystem(this.qualifiedName);
    this.spaceSystem$.then(spaceSystem => {
      this.historyDataSource.data = (spaceSystem.history || []).reverse();
    });
  }

  applyFilter(value: string) {
    this.historyDataSource.filter = value.trim().toLowerCase();
  }
}
