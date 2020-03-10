import { CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { map } from 'rxjs/operators';
import { CommandSubscription, StorageClient } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { printCommandId } from '../../shared/utils';
import { CommandHistoryRecord } from '../command-history/CommandHistoryRecord';
import { AddCommandDialog, CommandResult } from './AddCommandDialog';
import { CommandArgument, StackEntry } from './StackEntry';

@Component({
  templateUrl: './StackFilePage.html',
  styleUrls: ['./StackFilePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StackFilePage implements OnDestroy {

  instance: string;
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

  private commandSubscription: CommandSubscription;
  selectedEntry$ = new BehaviorSubject<StackEntry | null>(null);
  private commandHistoryRecords = new Map<string, CommandHistoryRecord>();

  @ViewChild('entryParent')
  private entryParent: ElementRef;

  private executionCounter = 0;

  constructor(
    private dialog: MatDialog,
    private yamcs: YamcsService,
    private route: ActivatedRoute,
    private title: Title,
  ) {
    this.instance = yamcs.getInstance();
    this.storageClient = yamcs.createStorageClient();

    const initialObject = this.getObjectNameFromUrl();
    this.loadFile(initialObject);

    this.commandSubscription = yamcs.yamcsClient.createCommandSubscription({
      instance: this.instance,
      processor: yamcs.getProcessor().name,
      ignorePastCommands: true,
    }, entry => {
      const id = printCommandId(entry.commandId);
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
    for (let i = 0; i < url.length; i++) {
      objectName += (i > 0) ? '/' + url[i].path : url[i].path;
    }
    return objectName;
  }

  private loadFile(objectName: string) {
    this.objectName = objectName;
    const idx = this.objectName.lastIndexOf('/');
    if (idx === -1) {
      this.folderLink = '/commanding/stacks/browse/';
      this.filename = this.objectName;
    } else {
      this.folderLink = '/commanding/stacks/browse/' + this.objectName.substring(0, idx);
      this.filename = this.objectName.substring(idx + 1);
    }

    this.title.setTitle(this.filename);

    this.storageClient.getObject('_global', 'stacks', objectName).then(response => {
      if (response.ok) {
        response.text().then(text => {
          const xmlParser = new DOMParser();
          const doc = xmlParser.parseFromString(text, 'text/xml') as XMLDocument;
          this.parseXML(doc.documentElement);
        });
      }
    });
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

    this.entries$.next(entries);
    this.selectedEntry$.next(entries.length ? entries[0] : null);
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

    const processor = this.yamcs.getProcessor().name;
    this.yamcs.yamcsClient.issueCommand(this.instance, processor, entry.name, {
      assignment: entry.arguments,
    }).then(response => {
      entry.executionNumber = ++this.executionCounter;
      entry.id = response.id;

      // It's possible the WebSocket received data before we
      // get our response.
      const rec = this.commandHistoryRecords.get(entry.id);
      if (rec) {
        entry.record = rec;
      }

      // Refresh subject, to be sure
      this.entries$.next([...this.entries$.value]);

      this.advanceSelection(entry);
    }).catch(err => {
      entry.executionNumber = ++this.executionCounter;
      entry.err = err.message || err;
      this.advanceSelection(entry);
    });
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
    const args: CommandArgument[] = [];
    for (let i = 0; i < node.childNodes.length; i++) {
      const child = node.childNodes[i] as Element;
      if (child.nodeName === 'commandArgument') {
        args.push({
          name: this.getStringAttribute(child, 'argumentName'),
          value: this.getStringAttribute(child, 'argumentValue'),
        });
      }
    }
    const entry: StackEntry = {
      name: this.getStringAttribute(node, 'qualifiedName'),
      arguments: args,
    };

    if (node.hasAttribute('comment')) {
      entry.comment = this.getStringAttribute(node, 'comment');
    }

    return entry;
  }

  addCommand() {
    const dialogRef = this.dialog.open(AddCommandDialog, {
      width: '70%',
      height: '100%',
      autoFocus: false,
      position: {
        right: '0',
      }
    });

    dialogRef.afterClosed().subscribe((result?: CommandResult) => {
      if (result) {
        const entry: StackEntry = {
          name: result.command.qualifiedName,
          arguments: result.assignments,
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

  saveStack() {
    const doc = document.implementation.createDocument(null, null, null);
    const rootEl = doc.createElement('commandStack');
    doc.appendChild(rootEl);

    for (const entry of this.entries$.value) {
      const entryEl = doc.createElement('command');
      entryEl.setAttribute('qualifiedName', entry.name);
      if (entry.comment) {
        entryEl.setAttribute('comment', entry.comment);
      }
      for (const argument of entry.arguments) {
        const argumentEl = doc.createElement('commandArgument');
        argumentEl.setAttribute('argumentName', argument.name);
        argumentEl.setAttribute('argumentValue', argument.value);
        entryEl.appendChild(argumentEl);
      }
      rootEl.appendChild(entryEl);
    }

    let xmlString = new XMLSerializer().serializeToString(rootEl);
    xmlString = this.formatXml(xmlString);

    const b = new Blob([xmlString]);
    this.storageClient.uploadObject('_global', 'stacks', this.objectName, b).then(() => {
      this.dirty$.next(false);
    });
  }

  private formatXml(xml: string) {
    let formatted = '';
    let indent = '';
    const spaces = '  ';
    xml.split(/>\s*</).forEach(function (node) {
      if (node.match(/^\/\w/)) indent = indent.substring(spaces.length);
      formatted += indent + '<' + node + '>\r\n';
      if (node.match(/^<?\w[^>]*[^\/]$/)) indent += spaces;
    });
    return formatted.substring(1, formatted.length - 3);
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
