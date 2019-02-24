import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Instance } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import { CommandHistoryDataSource } from './CommandHistoryDataSource';
import { CommandHistoryRecord } from './CommandHistoryRecord';


@Component({
  templateUrl: './CommandHistoryPage.html',
  styleUrls: ['./CommandHistoryPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandHistoryPage implements OnInit {

  instance: Instance;

  selectedRecord$ = new BehaviorSubject<CommandHistoryRecord | null>(null);

  displayedColumns = [
    'completion',
    'generationTimeUTC',
    'comment',
    'command',
    // 'source',
    // 'sourceID',
    'verifiers',
  ];

  dataSource: CommandHistoryDataSource;

  constructor(private yamcs: YamcsService, title: Title) {
    title.setTitle('Command History - Yamcs');
    this.instance = yamcs.getInstance();
  }

  // Used in table trackBy to prevent continuous row recreation
  // tableTrackerFn = (index: number, entry: CommandHistoryEntry) => ;

  ngOnInit() {
    this.dataSource = new CommandHistoryDataSource(this.yamcs);
    this.dataSource.loadEntries('realtime');
  }

  selectRecord(rec: CommandHistoryRecord) {
    this.selectedRecord$.next(rec);
  }
}
