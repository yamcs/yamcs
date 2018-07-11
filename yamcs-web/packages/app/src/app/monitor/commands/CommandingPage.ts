import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { CommandHistoryEntry, Instance } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import { CommandHistoryDataSource } from './CommandHistoryDataSource';


@Component({
  templateUrl: './CommandingPage.html',
  styleUrls: ['./CommandingPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandingPage implements OnInit {

  instance: Instance;

  selectedEntry$ = new BehaviorSubject<CommandHistoryEntry | null>(null);

  displayedColumns = [
    'completion',
    'generationTimeUTC',
    'command',
    'source',
    'sourceID',
    'sequenceNumber',
  ];

  dataSource: CommandHistoryDataSource;

  constructor(private yamcs: YamcsService, title: Title) {
    title.setTitle('Commanding - Yamcs');
    this.instance = yamcs.getInstance();
  }

  // Used in table trackBy to prevent continuous row recreation
  // tableTrackerFn = (index: number, entry: CommandHistoryEntry) => ;

  ngOnInit() {
    this.dataSource = new CommandHistoryDataSource(this.yamcs);
    this.dataSource.loadEntries('realtime');
  }

  selectEntry(entry: CommandHistoryEntry) {
    this.selectedEntry$.next(entry);
  }

  getUsername(entry: CommandHistoryEntry) {
    for (const attr of entry.attr) {
      if (attr.name === 'username') {
        return attr.value.stringValue;
      }
    }
  }

  getCommandString(entry: CommandHistoryEntry) {
    for (const attr of entry.attr) {
      if (attr.name === 'source') {
        return attr.value.stringValue;
      }
    }
  }

  getCommandBinary(entry: CommandHistoryEntry) {
    for (const attr of entry.attr) {
      if (attr.name === 'binary') {
        return attr.value.binaryValue;
      }
    }
  }

  getFailedReason(entry: CommandHistoryEntry) {
    for (const attr of entry.attr) {
      if (attr.name === 'CommandFailed') {
        return attr.value.stringValue;
      }
    }
  }

  getFinalSequenceCount(entry: CommandHistoryEntry) {
    for (const attr of entry.attr) {
      if (attr.name === 'Final_Sequence_Count') {
        return attr.value.stringValue;
      }
    }
  }

  isCompleted(entry: CommandHistoryEntry) {
    for (const attr of entry.attr) {
      if (attr.name === 'CommandComplete') {
        return attr.value.stringValue === 'OK';
      }
    }
  }

  isFailed(entry: CommandHistoryEntry) {
    for (const attr of entry.attr) {
      if (attr.name === 'CommandComplete') {
        return attr.value.stringValue === 'NOK';
      }
    }
  }
}
