import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { StackedCommand } from './StackedCommand';

@Component({
  templateUrl: './CommandStackPage.html',
  styleUrls: ['./CommandStackPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandStackPage {

  dataSource = new MatTableDataSource<StackedCommand>([]);

  displayedColumns = [
    'name',
  ];

  constructor(title: Title) {
    title.setTitle('Command Stack - Yamcs');
    this.dataSource.data = [{
      command: 'test1',
    }, {
      command: 'test2',
    }];
  }
}
