import { Component, ChangeDetectionStrategy, OnInit } from '@angular/core';

import { Instance, CommandHistoryEntry } from '@yamcs/client';

import { YamcsService } from '../../core/services/YamcsService';
import { CommandHistoryDataSource } from './CommandHistoryDataSource';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { Title } from '@angular/platform-browser';

@Component({
  templateUrl: './CommandsPage.html',
  styleUrls: ['./CommandsPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandsPage implements OnInit {

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
    title.setTitle('Commands - Yamcs');
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
