import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Instance } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { rowAnimation } from '../../animations';
import { Synchronizer } from '../../core/services/Synchronizer';
import { YamcsService } from '../../core/services/YamcsService';
import { CommandHistoryDataSource } from './CommandHistoryDataSource';
import { CommandHistoryRecord } from './CommandHistoryRecord';


@Component({
  templateUrl: './CommandHistoryPage.html',
  styleUrls: ['./CommandHistoryPage.css'],
  animations: [rowAnimation],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandHistoryPage {

  instance: Instance;

  selectedRecord$ = new BehaviorSubject<CommandHistoryRecord | null>(null);

  displayedColumns = [
    'completion',
    'generationTimeUTC',
    'comment',
    'command',
    // 'source',
    // 'sourceID',
    'stages',
  ];

  dataSource: CommandHistoryDataSource;

  constructor(private yamcs: YamcsService, title: Title, synchronizer: Synchronizer) {
    title.setTitle('Command History - Yamcs');
    this.instance = yamcs.getInstance();

    this.dataSource = new CommandHistoryDataSource(this.yamcs, synchronizer);
    this.dataSource.loadEntries('realtime');
  }

  jumpToNow() {
    this.dataSource.loadEntries('realtime');
  }

  // Used in table trackBy to prevent continuous row recreation
  // tableTrackerFn = (index: number, entry: CommandHistoryEntry) => ;

  loadMoreData() {
    this.dataSource.loadMoreData({});
  }

  selectRecord(rec: CommandHistoryRecord) {
    this.selectedRecord$.next(rec);
  }
}
