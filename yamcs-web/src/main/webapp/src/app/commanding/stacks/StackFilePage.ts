import { CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { FormBuilder, UntypedFormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { DomSanitizer, SafeResourceUrl, Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { map } from 'rxjs/operators';
import { MessageService } from 'src/app/core/services/MessageService';
import { ExtensionPipe } from 'src/app/shared/pipes/ExtensionPipe';
import { FilenamePipe } from 'src/app/shared/pipes/FilenamePipe';
import { Command, CommandSubscription, StorageClient, Value } from '../../client';
import { ConfigService } from '../../core/services/ConfigService';
import { YamcsService } from '../../core/services/YamcsService';
import { CommandHistoryRecord } from '../command-history/CommandHistoryRecord';
import { CommandResult, EditStackEntryDialog } from './EditStackEntryDialog';
import { AdvanceOnParams, StackEntry } from './StackEntry';
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

  loaded = false;
  format: "json" | "xml";

  JSONblobURL: SafeResourceUrl;
  XMLblobURL: SafeResourceUrl;

  private advanceOn: AdvanceOnParams | undefined; // Values from JSON, to not save the defaults unnecessarily

  private delayBetweenCommands = -1;
  // Configuration of command ack to accept to execute the following
  // private acceptingAck = "NONE";
  private acceptingAck = "Acknowledge_Queued";
  // Default: OK/DISABLED -> continue, NOK/CANCELLED/after timeout -> pause,  everything else -> ignore
  private acceptingAckValues = ["OK", "DISABLED"];
  private stoppingAckValues = ["NOK", "CANCELLED"];
  private stoppingAckTimeout = -1;

  private runningTimeout: NodeJS.Timeout;
  private nextCommandDelayTimeout: NodeJS.Timeout;
  private nextCommandScheduled = false;

  stackOptionsForm: UntypedFormGroup;

  predefinedAcks = {
    Acknowledge_Queued: "Queued",
    Acknowledge_Released: "Released",
    Acknowledge_Sent: "Sent",
    CommandComplete: "Completed"
  };
  predefinedAcksArray = Object.entries(this.predefinedAcks).map(ack => { return { name: ack[0], verboseName: ack[1] }; });

  constructor(
    private dialog: MatDialog,
    readonly yamcs: YamcsService,
    private route: ActivatedRoute,
    private title: Title,
    private configService: ConfigService,
    private messageService: MessageService,
    private sanitizer: DomSanitizer,
    private formBuilder: FormBuilder
  ) {
    const config = configService.getConfig();
    this.bucket = config.stackBucket;
    this.folderPerInstance = config.displayFolderPerInstance;
    this.storageClient = yamcs.createStorageClient();

    const initialObject = this.getObjectNameFromUrl();

    this.loadFile(initialObject);

    this.stackOptionsForm = formBuilder.group({
      advanceOnAckDropDown: ['', []],
      advanceOnAckCustom: ['', []],
      advanceOnDelay: ['', []],
    });

    if (this.format !== "json") {
      this.stackOptionsForm.disable();
    } else {
      this.stackOptionsForm.valueChanges.subscribe(result => {
        if (!this.loaded) {
          return;
        }

        if ((result.advanceOnAckDropDown && result.advanceOnAckDropDown !== "custom") ||
          (result.advanceOnAckDropDown === "custom" && result.advanceOnAckCustom) || result.advanceOnDelay != null) {
          this.advanceOn = {
            ...(result.advanceOnAckDropDown && result.advanceOnAckDropDown !== "custom" && { ack: result.advanceOnAckDropDown }),
            ...(result.advanceOnAckDropDown === "custom" && result.advanceOnAckCustom && { ack: result.advanceOnAckCustom }),
            ...(result.advanceOnDelay != null && { delay: result.advanceOnDelay })
          };
        } else {
          this.advanceOn = undefined;
        }
        this.acceptingAck = this.advanceOn?.ack || "Acknowledge_Queued";
        this.delayBetweenCommands = this.advanceOn?.delay != null ? this.advanceOn.delay : -1;

        this.dirty$.next(true);
      });
    }

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

      console.log(rec);

      // Continue running entries
      this.checkAckContinueRunning(rec);
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

    const format = new ExtensionPipe().transform(new FilenamePipe().transform(objectName))?.toLowerCase();
    if (format === "json" || format === "xml") {
      this.format = format;
    } else {
      this.loaded = true;
      return;
    }

    const response = await this.storageClient.getObject('_global', this.bucket, objectName);
    if (response.ok) {
      const text = await response.text();
      let entries;
      switch (this.format) {
        case "json":
          entries = this.parseJSON(text);

          let advanceOnAckDropDownDefault = this.advanceOn?.ack &&
            (Object.keys(this.predefinedAcks).includes(this.advanceOn?.ack) || this.advanceOn?.ack === "NONE" ? this.advanceOn?.ack : "custom");
          let advanceOnAckCustomDefault = !Object.keys(this.predefinedAcks).includes(this.advanceOn?.ack || '') && this.advanceOn?.ack !== "NONE" ? this.advanceOn?.ack : '';

          this.stackOptionsForm.setValue({
            advanceOnAckDropDown: advanceOnAckDropDownDefault || '',
            advanceOnAckCustom: advanceOnAckCustomDefault || '',
            advanceOnDelay: this.advanceOn?.delay != null ? this.advanceOn?.delay : ''
          });
          break;
        case "xml":
          const xmlParser = new DOMParser();
          const doc = xmlParser.parseFromString(text, 'text/xml') as XMLDocument;
          entries = this.parseXML(doc.documentElement);
          this.messageService.showWarning("XML formatted command stacks are deprecated, consider exporting and re-importing as JSON");
          break;
      }

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
      this.loaded = true;
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

  private parseJSON(json: string) {
    const entries: StackEntry[] = [];

    const stack = JSON.parse(json);
    if (stack.commands) {
      for (let command of stack.commands) {
        entries.push(this.parseJSONentry(command));
      }
    }

    if (stack.advanceOn) {
      this.advanceOn = {};
      if (stack.advanceOn.ack) {
        this.advanceOn.ack = stack.advanceOn.ack;
        this.acceptingAck = stack.advanceOn.ack;
      }
      if (stack.advanceOn.delay != null) {
        this.advanceOn.delay = stack.advanceOn.delay;
        this.delayBetweenCommands = stack.advanceOn.delay;
      }
    }

    return entries;
  }

  private parseJSONentry(command: any) {
    const entry: StackEntry = {
      name: command.name,
      ...(command.comment && { comment: command.comment }),
      ...(command.extraOptions && { extra: this.parseJSONextraOptions(command.extraOptions) }),
      ...(command.arguments && { args: this.parseJSONcommandArguments(command.arguments) }),
      ...(command.advanceOn && { advanceOn: command.advanceOn })
    };
    return entry;
  }

  private parseJSONextraOptions(options: Array<any>) {
    const extra: { [key: string]: Value; } = {};
    for (let option of options) {
      const value = this.convertOptionStringToValue(option.id, (new String(option.value)).toString()); // TODO: simplify value conversion
      if (value) {
        extra[option.id] = value;
      }
    }
    return extra;
  }

  private parseJSONcommandArguments(commandArguments: Array<any>) {
    const args: { [key: string]: any; } = {};
    for (let argument of commandArguments) {
      args[argument.name] = argument.value;
    }
    return args;
  }

  private parseXML(root: Node) {
    const entries: StackEntry[] = [];
    for (let i = 0; i < root.childNodes.length; i++) {
      const child = root.childNodes[i];
      if (child.nodeType !== 3) { // Ignore text or whitespace
        if (child.nodeName === 'command') {
          const entry = this.parseXMLEntry(child as Element);
          entries.push(entry);
        }
      }
    }

    return entries;
  }

  private parseXMLEntry(node: Element): StackEntry {
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
      if (this.stoppingAckTimeout > 0) {
        this.runningTimeout = setTimeout(() => {
          if (this.selectedEntry$.value?.id === response.id) {
            this.messageService.showWarning("Command timeout reached, stopping current run");
            this.stopRun();
          }
        }, this.stoppingAckTimeout);
      }

      entry.executionNumber = executionNumber;
      entry.id = response.id;

      // It's possible the WebSocket received data before we
      // get our response.
      const rec = this.commandHistoryRecords.get(entry.id);
      if (rec) {
        entry.record = rec;
        this.checkAckContinueRunning(rec);
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
    this.nextCommandScheduled = false;
    try {
      await this.runEntry(entry);
    } finally {
      if (this.entries$.value.indexOf(entry) === this.entries$.value.length - 1) {
        this.advanceSelection(entry);
        this.stopRun();
      }
    }
  }

  private checkAckContinueRunning(record: CommandHistoryRecord) {
    // Attempts to continue the current run from selected by checking acknowledgement statuses
    if (!this.running$.value || this.nextCommandScheduled) {
      return;
    }

    let selectedEntry = this.selectedEntry$.value;

    let acceptingAck = selectedEntry?.advanceOn?.ack || this.acceptingAck;
    let delay = selectedEntry?.advanceOn?.delay != null ? selectedEntry?.advanceOn?.delay : this.delayBetweenCommands;

    if (acceptingAck === "NONE") {
      this.nextCommandScheduled = true;
      this.nextCommandDelayTimeout = setTimeout(() => {
        // Continue execution
        if (this.running$.value && selectedEntry) {
          this.advanceSelection(selectedEntry);
          this.runFromSelection();
        }
      }, Math.max(delay, 0));
      return;
    }

    if (selectedEntry?.id === record.id) {
      let ack = record.acksByName[acceptingAck];
      if (ack && ack.status) {
        if (this.acceptingAckValues.includes(ack.status)) {
          this.nextCommandScheduled = true;
          this.nextCommandDelayTimeout = setTimeout(() => {
            // Continue execution
            if (this.running$.value && selectedEntry) {
              this.advanceSelection(selectedEntry);
              this.runFromSelection();
            }
          }, Math.max(delay, 0));
        } else if (this.stoppingAckValues.includes(ack.status)) {
          // Stop exection
          this.stopRun();
        } // Ignore others
      }
    }
  }

  stopRun() {
    this.running$.next(false);
    clearTimeout(this.nextCommandDelayTimeout);
    clearTimeout(this.runningTimeout);
    this.nextCommandScheduled = false;
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
        format: this.format
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
          ...(result.stackOptions.advanceOn && { advanceOn: result.stackOptions.advanceOn })
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
        format: this.format
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
          ...(result.stackOptions.advanceOn && { advanceOn: result.stackOptions.advanceOn })
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
    let file;
    switch (this.format) {
      case "json":
        file = new StackFormatter(this.entries$.value, { advanceOn: this.advanceOn }).toJSON();
        break;
      case "xml":
        file = new StackFormatter(this.entries$.value, { advanceOn: this.advanceOn }).toXML();
        break;
    }
    const blob = new Blob([file], { type: 'application/' + this.format });
    this.storageClient.uploadObject('_global', this.bucket, this.objectName, blob).then(() => {
      this.dirty$.next(false);
    });
  }

  setExportURLs() {
    const json = new StackFormatter(this.entries$.value, { advanceOn: this.advanceOn }).toJSON();
    const JSONblob = new Blob([json], { type: 'application/json' });
    this.JSONblobURL = this.sanitizer.bypassSecurityTrustResourceUrl(URL.createObjectURL(JSONblob));

    const xml = new StackFormatter(this.entries$.value, { advanceOn: this.advanceOn }).toXML();
    const XMLblob = new Blob([xml], { type: 'application/xml' });
    this.XMLblobURL = this.sanitizer.bypassSecurityTrustResourceUrl(URL.createObjectURL(XMLblob));
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
    this.commandSubscription?.cancel();
    this.stackOptionsForm.reset();
  }
}
