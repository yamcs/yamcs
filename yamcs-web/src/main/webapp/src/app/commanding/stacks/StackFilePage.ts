import { CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { map } from 'rxjs/operators';
import { Command, CommandSubscription, StorageClient, Value } from '../../client';
import { ConfigService } from '../../core/services/ConfigService';
import { YamcsService } from '../../core/services/YamcsService';
import { CommandHistoryRecord } from '../command-history/CommandHistoryRecord';
import { CommandResult, EditStackEntryDialog } from './EditStackEntryDialog';
import { StackEntry } from './StackEntry';
import { StackFormatter } from './StackFormatter';

@Component({
  templateUrl: './StackFilePage.html',
  styleUrls: ['./StackFilePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StackFilePage implements OnDestroy {

  private storageClient: StorageClient;

  objectName: string;
  filename: string;
  folderLink: string;

  dirty$ = new BehaviorSubject<boolean>(false);
  entries$ = new BehaviorSubject<StackEntry[]>([]);
  hasOutputs$ = this.entries$.pipe(
    map(entries => {
      for (const entry of entries) {
        if (entry.record) {
          return true;
        }
      }
      return false;
    })
  );

  running$ = new BehaviorSubject<boolean>(false);

  private commandSubscription: CommandSubscription;
  selectedEntry$ = new BehaviorSubject<StackEntry | null>(null);
  clipboardEntry$ = new BehaviorSubject<StackEntry | null>(null);
  private commandHistoryRecords = new Map<string, CommandHistoryRecord>();

  @ViewChild('entryParent')
  private entryParent: ElementRef;

  private executionCounter = 0;

  private bucket: string;
  private folderPerInstance: boolean;

  constructor(
    private dialog: MatDialog,
    readonly yamcs: YamcsService,
    private route: ActivatedRoute,
    private title: Title,
    private configService: ConfigService,
  ) {
    const config = configService.getConfig();
    this.bucket = config.stackBucket;
    this.folderPerInstance = config.displayFolderPerInstance;
    this.storageClient = yamcs.createStorageClient();

    const initialObject = this.getObjectNameFromUrl();
    this.loadFile(initialObject);

    this.commandSubscription = yamcs.yamcsClient.createCommandSubscription({
      instance: yamcs.instance!,
      processor: yamcs.processor!,
      ignorePastCommands: true,
    }, entry => {
      const id = entry.id;
      let rec = this.commandHistoryRecords.get(id);
      if (rec) {
        rec = rec.mergeEntry(entry);
      } else {
        rec = new CommandHistoryRecord(entry);
      }
      this.commandHistoryRecords.set(id, rec);

      // Try to find an entry with matching id. If it's there
      // update it. If not: no problem, the link will be made when
      // handling the command response.
      for (const entry of this.entries$.value) {
        if (entry.id === id) {
          entry.record = rec;
          break;
        }
      }
    });
  }

  handleDrop(event: CdkDragDrop<StackEntry[]>) {
    if (event.previousIndex !== event.currentIndex) {
      moveItemInArray(this.entries$.value, event.previousIndex, event.currentIndex);
      this.entries$.next([...this.entries$.value]);
      this.dirty$.next(true);
    }
  }

  private getObjectNameFromUrl() {
    const url = this.route.snapshot.url;
    let objectName = '';
    if (this.folderPerInstance) {
      objectName += this.yamcs.instance!;
    }
    for (const segment of url) {
      if (objectName) {
        objectName += '/';
      }
      objectName += segment.path;
    }
    return objectName;
  }

  private getNameWithoutInstance(name: string) {
    if (this.folderPerInstance) {
      const instance = this.yamcs.instance!;
      return name.substr(instance.length);
    } else {
      return name;
    }
  }

  private async loadFile(objectName: string) {
    this.objectName = objectName;
    const idx = this.objectName.lastIndexOf('/');
    if (idx === -1) {
      this.folderLink = '/commanding/stacks/browse/';
      this.filename = this.objectName;
    } else {
      const folderWithoutInstance = this.getNameWithoutInstance(this.objectName.substring(0, idx));
      this.folderLink = '/commanding/stacks/browse/' + folderWithoutInstance;
      this.filename = this.objectName.substring(idx + 1);
    }

    this.title.setTitle(this.filename);

    const response = await this.storageClient.getObject('_global', this.bucket, objectName);
    if (response.ok) {
      const text = await response.text();
      const xmlParser = new DOMParser();
      const doc = xmlParser.parseFromString(text, 'text/xml') as XMLDocument;

      const entries = this.parseXML(doc.documentElement);

      // Enrich entries with MDB info, it's used in the detail panel
      const promises = [];
      for (const entry of entries) {
        promises.push(this.yamcs.yamcsClient.getCommand(this.yamcs.instance!, entry.name).then(command => {
          entry.command = command;
        }));
      }

      // Wait on all definition requests to arrive
      for (const promise of promises) {
        try {
          await promise;
        } catch {
          // For now, don't care
        }
      }

      // Convert arrays/aggregates from JSON to JavaScript
      for (const entry of entries) {
        if (entry.command) {
          for (const argumentName in entry.args) {
            if (this.isComplex(argumentName, entry.command)) {
              entry.args[argumentName] = JSON.parse(entry.args[argumentName]);
            }
          }
        }
      }

      // Only now, update the page
      this.entries$.next(entries);
      this.selectedEntry$.next(entries.length ? entries[0] : null);
    }
  }

  private isComplex(argumentName: string, info: Command): boolean {
    for (const argument of (info.argument || [])) {
      if (argument.name === argumentName) {
        return argument.type.engType === 'aggregate'
          || argument.type.engType.endsWith('[]');
      }
    }
    if (info.baseCommand) {
      return this.isComplex(argumentName, info.baseCommand);
    } else {
      return false;
    }
  }

  private parseXML(root: Node) {
    const entries: StackEntry[] = [];
    for (let i = 0; i < root.childNodes.length; i++) {
      const child = root.childNodes[i];
      if (child.nodeType !== 3) { // Ignore text or whitespace
        if (child.nodeName === 'command') {
          const entry = this.parseEntry(child as Element);
          entries.push(entry);
        }
      }
    }

    return entries;
  }

  selectEntry(entry: StackEntry) {
    this.selectedEntry$.next(entry);
  }

  deleteSelectedCommands() {
    const entry = this.selectedEntry$.value;
    if (entry) {
      const idx = this.entries$.value.indexOf(entry);
      if (idx !== -1) {
        this.entries$.value.splice(idx, 1);
        this.entries$.next([...this.entries$.value]);
        this.selectedEntry$.next(null);
        this.dirty$.next(true);
      }
    }
  }

  clearSelectedOutputs() {
    const entry = this.selectedEntry$.value;
    if (entry) {
      entry.executionNumber = undefined;
      entry.id = undefined;
      entry.record = undefined;
      entry.err = undefined;
    }
  }

  clearOutputs() {
    for (const entry of this.entries$.value) {
      entry.executionNumber = undefined;
      entry.id = undefined;
      entry.record = undefined;
      entry.err = undefined;
    }
    this.executionCounter = 0;
    this.entries$.next([...this.entries$.value]);
    if (this.entries$.value.length) {
      this.selectEntry(this.entries$.value[0]);
    }
  }

  hasPendingChanges() {
    return this.dirty$.value;
  }

  runSelection() {
    const entry = this.selectedEntry$.value;
    if (!entry) {
      return;
    }

    this.runEntry(entry).finally(() => {
      this.advanceSelection(entry);
    });
  }

  private async runEntry(entry: StackEntry) {
    const executionNumber = ++this.executionCounter;
    return this.yamcs.yamcsClient.issueCommand(this.yamcs.instance!, this.yamcs.processor!, entry.name, {
      sequenceNumber: executionNumber,
      args: entry.args,
      extra: entry.extra,
    }).then(response => {
      entry.executionNumber = executionNumber;
      entry.id = response.id;

      // It's possible the WebSocket received data before we
      // get our response.
      const rec = this.commandHistoryRecords.get(entry.id);
      if (rec) {
        entry.record = rec;
      }

      // Refresh subject, to be sure
      this.entries$.next([...this.entries$.value]);
    }).catch(err => {
      entry.executionNumber = executionNumber;
      entry.err = err.message || err;
    });
  }

  async runFromSelection() {
    let entry = this.selectedEntry$.value;
    if (!entry) {
      return;
    }

    this.running$.next(true);
    try {
      const entries = this.entries$.value;
      for (let i = entries.indexOf(entry); i < entries.length; i++) {
        entry = entries[i];
        await this.runEntry(entry);
        this.advanceSelection(entry);
      }
    } finally {
      this.running$.next(false);
    }
  }

  stopRun() {
    this.running$.next(false);
  }

  private advanceSelection(entry: StackEntry) {
    const entries = this.entries$.value;
    const idx = entries.indexOf(entry);
    if (idx < entries.length - 1) {
      const nextEntry = entries[idx + 1];
      this.selectEntry(nextEntry);

      const parentEl = this.entryParent.nativeElement as HTMLDivElement;
      const entryEl = parentEl.getElementsByClassName('entry')[idx + 1] as HTMLDivElement;
      if (!this.isVisible(entryEl)) {
        entryEl.scrollIntoView();
      }
    } else {
      this.selectedEntry$.next(null);
    }
  }

  private isVisible(el: HTMLDivElement) {
    const rect = el.getBoundingClientRect();
    const viewHeight = Math.max(document.documentElement.clientHeight, window.innerHeight);
    return !(rect.bottom < 0 || rect.top - viewHeight >= 0);
  }

  private parseEntry(node: Element): StackEntry {
    const args: { [key: string]: any; } = {};
    const extra: { [key: string]: Value; } = {};
    for (let i = 0; i < node.childNodes.length; i++) {
      const child = node.childNodes[i] as Element;
      if (child.nodeName === 'commandArgument') {
        const argumentName = this.getStringAttribute(child, 'argumentName');
        args[argumentName] = this.getStringAttribute(child, 'argumentValue');
      } else if (child.nodeName === 'extraOptions') {
        for (let j = 0; j < child.childNodes.length; j++) {
          const extraChild = child.childNodes[j] as Element;
          if (extraChild.nodeName === 'extraOption') {
            const id = this.getStringAttribute(extraChild, 'id');
            const stringValue = this.getStringAttribute(extraChild, 'value');
            const value = this.convertOptionStringToValue(id, stringValue);
            if (value) {
              extra[id] = value;
            }
          }
        }
      }
    }
    const entry: StackEntry = {
      name: this.getStringAttribute(node, 'qualifiedName'),
      args,
      extra,
    };

    if (node.hasAttribute('comment')) {
      entry.comment = this.getStringAttribute(node, 'comment');
    }

    return entry;
  }

  private convertOptionStringToValue(id: string, value: string): Value | null {
    for (const option of this.configService.getCommandOptions()) {
      if (option.id === id) {
        switch (option.type) {
          case 'BOOLEAN':
            return { type: 'BOOLEAN', booleanValue: value === 'true' };
          case 'NUMBER':
            return { type: 'SINT32', sint32Value: Number(value) };
          default:
            return { type: 'STRING', stringValue: value };
        }
      }
    }
    return null;
  }

  addCommand() {
    const dialogRef = this.dialog.open(EditStackEntryDialog, {
      width: '70%',
      height: '100%',
      autoFocus: false,
      position: {
        right: '0',
      },
      data: {
        okLabel: 'ADD TO STACK',
      }
    });

    dialogRef.afterClosed().subscribe((result?: CommandResult) => {
      if (result) {
        const entry: StackEntry = {
          name: result.command.qualifiedName,
          args: result.args,
          comment: result.comment,
          extra: result.extra,
          command: result.command,
        };

        const relto = this.selectedEntry$.value;
        if (relto) {
          const entries = this.entries$.value;
          const idx = entries.indexOf(relto);
          entries.splice(idx + 1, 0, entry);
          this.entries$.next([...this.entries$.value]);
        } else {
          this.entries$.next([... this.entries$.value, entry]);
        }
        this.selectEntry(entry);
        this.dirty$.next(true);
      }
    });
  }

  editSelectedCommand() {
    const entry = this.selectedEntry$.value!;
    this.editCommand(entry);
  }

  editCommand(entry: StackEntry) {
    this.selectEntry(entry);

    const dialogRef = this.dialog.open(EditStackEntryDialog, {
      width: '70%',
      height: '100%',
      autoFocus: false,
      position: {
        right: '0',
      },
      data: {
        okLabel: 'UPDATE',
        entry,
      }
    });

    dialogRef.afterClosed().subscribe((result?: CommandResult) => {
      if (result) {
        const changedEntry: StackEntry = {
          name: result.command.qualifiedName,
          args: result.args,
          comment: result.comment,
          extra: result.extra,
          command: result.command,
        };

        const entries = this.entries$.value;
        const idx = entries.indexOf(entry);
        entries.splice(idx, 1, changedEntry);
        this.entries$.next([...this.entries$.value]);

        this.selectEntry(changedEntry);
        this.dirty$.next(true);
      }
    });

    return false;
  }

  cutSelectedCommand() {
    const entry = this.selectedEntry$.value!;
    this.advanceSelection(entry);
    this.clipboardEntry$.next(entry);

    const entries = this.entries$.value;
    const idx = entries.indexOf(entry);
    entries.splice(idx, 1);
    this.entries$.next([...this.entries$.value]);

    this.dirty$.next(true);
  }

  copySelectedCommand() {
    const entry = this.selectedEntry$.value!;
    this.clipboardEntry$.next(entry);
  }

  pasteCommand() {
    const entry = this.clipboardEntry$.value;
    if (entry) {
      const copiedEntry: StackEntry = {
        name: entry.name,
        args: { ...entry.args },
        comment: entry.comment,
        command: entry.command,
      };
      if (entry.extra) {
        copiedEntry.extra = { ...entry.extra };
      }

      const relto = this.selectedEntry$.value;
      if (relto) {
        const entries = this.entries$.value;
        const idx = entries.indexOf(relto);
        entries.splice(idx + 1, 0, copiedEntry);
        this.entries$.next([...this.entries$.value]);
      } else {
        this.entries$.next([... this.entries$.value, copiedEntry]);
      }
      this.selectEntry(copiedEntry);
      this.dirty$.next(true);
    }
  }

  saveStack() {
    const xml = new StackFormatter(this.entries$.value).toXML();
    const b = new Blob([xml]);
    this.storageClient.uploadObject('_global', this.bucket, this.objectName, b).then(() => {
      this.dirty$.next(false);
    });
  }

  private getStringAttribute(node: Node, name: string) {
    const attr = (node as Element).attributes.getNamedItem(name);
    if (attr === null) {
      throw new Error(`No attribute named ${name}`);
    } else {
      return attr.textContent || '';
    }
  }

  ngOnDestroy() {
    if (this.commandSubscription) {
      this.commandSubscription.cancel();
    }
  }
}
