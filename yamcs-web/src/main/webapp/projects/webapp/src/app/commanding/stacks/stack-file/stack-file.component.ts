import { CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';
import { KeyValue } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { FormBuilder, UntypedFormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { DomSanitizer, SafeResourceUrl, Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { AcknowledgmentInfo, AdvancementParams, Argument, Command, CommandHistoryRecord, CommandSubscription, ConfigService, CreateTimelineItemRequest, MessageService, StackEntry, StackFormatter, StorageClient, Value, WebappSdkModule, YaSelectOption, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { map } from 'rxjs/operators';
import { AuthService } from '../../../core/services/AuthService';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { ExtraAcknowledgmentsTableComponent } from '../../command-history/extra-acknowledgments-table/extra-acknowledgments-table.component';
import { YamcsAcknowledgmentsTableComponent } from '../../command-history/yamcs-acknowledgments-table/yamcs-acknowledgments-table.component';
import { AcknowledgmentNamePipe } from '../acknowledgment-name.pipe';
import { AdvanceAckHelpComponent } from '../advance-ack-help/advance-ack-help.component';
import { CommandResult, EditStackEntryDialogComponent } from '../edit-stack-entry-dialog/edit-stack-entry-dialog.component';
import { ScheduleStackDialogComponent } from '../schedule-stack-dialog/schedule-stack-dialog.component';
import { StackedCommandDetailComponent } from '../stacked-command-detail/stacked-command-detail.component';

@Component({
  standalone: true,
  templateUrl: './stack-file.component.html',
  styleUrl: './stack-file.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AcknowledgmentNamePipe,
    AdvanceAckHelpComponent,
    ExtraAcknowledgmentsTableComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
    StackedCommandDetailComponent,
    YamcsAcknowledgmentsTableComponent,
  ],
})
export class StackFileComponent implements OnDestroy {
  private storageClient: StorageClient;

  objectName: string;
  filename: string;
  folderLink: string;

  dirty$ = new BehaviorSubject<boolean>(false);
  entries$ = new BehaviorSubject<StackEntry[]>([]);
  hasOutputs$ = this.entries$.pipe(
    map(entries => {
      for (const entry of entries) {
        if (entry.record || entry.err) {
          return true;
        }
      }
      return false;
    })
  );

  running$ = new BehaviorSubject<boolean>(false);
  runningStack = false;

  private commandSubscription: CommandSubscription;
  selectedEntry$ = new BehaviorSubject<StackEntry | null>(null);
  clipboardEntry$ = new BehaviorSubject<StackEntry | null>(null);
  private commandHistoryRecords = new Map<string, CommandHistoryRecord>();

  @ViewChild('entryParent')
  private entryParent: ElementRef;

  private executionCounter = 0;

  private bucket: string;

  loaded = false;
  format: "ycs" | "xml";

  JSONblobURL: SafeResourceUrl;
  XMLblobURL: SafeResourceUrl;

  converting = false;

  // Configuration of command ack to accept to execute the following
  private advancement: AdvancementParams = { acknowledgment: "Acknowledge_Queued", wait: 0 };

  // Default: OK/DISABLED -> continue, NOK/CANCELLED/after timeout -> pause,  everything else -> ignore
  private acceptingAckValues = ["OK", "DISABLED"];
  private stoppingAckValues = ["NOK", "CANCELLED"];

  private nextCommandDelayTimeout: number;
  private nextCommandScheduled = false;

  stackOptionsForm: UntypedFormGroup;
  extraAcknowledgments: AcknowledgmentInfo[];
  ackOptions: YaSelectOption[] = [
    { id: 'Acknowledge_Queued', label: 'Queued' },
    { id: 'Acknowledge_Released', label: 'Released' },
    { id: 'Acknowledge_Sent', label: 'Sent' },
    { id: 'CommandComplete', label: 'Completed' },
  ];

  // KeyValuePipe comparator that preserves original order.
  // (default KeyValuePipe is to sort A-Z, but that's undesired for args).
  insertionOrder = (a: KeyValue<string, any>, b: KeyValue<string, any>): number => {
    return 0;
  };

  constructor(
    private dialog: MatDialog,
    readonly yamcs: YamcsService,
    private route: ActivatedRoute,
    private router: Router,
    private title: Title,
    private authService: AuthService,
    private configService: ConfigService,
    private messageService: MessageService,
    private sanitizer: DomSanitizer,
    formBuilder: FormBuilder
  ) {
    const config = configService.getConfig();
    this.bucket = configService.getStackBucket();
    this.storageClient = yamcs.createStorageClient();

    this.extraAcknowledgments = yamcs.getProcessor()?.acknowledgments ?? [];
    let first = true;
    for (const ack of this.extraAcknowledgments) {
      this.ackOptions.push({
        id: ack.name,
        label: ack.name.replace('Acknowledge_', ''),
        group: first,
      });
      first = false;
    }

    this.ackOptions.push({
      id: 'custom',
      label: 'Custom',
      group: true,
    });

    this.stackOptionsForm = formBuilder.group({
      advancementAckDropDown: ['', []],
      advancementAckCustom: ['', []],
      advancementWait: ['', []],
    });

    this.initStackFile();
  }

  private initStackFile() {
    const initialObject = this.getObjectNameFromUrl();

    this.loadFile(initialObject);

    if (this.format !== "ycs") {
      this.stackOptionsForm.disable();
    } else {
      this.stackOptionsForm.enable();
      this.stackOptionsForm.valueChanges.subscribe(this.stackOptionsFormCallback);
    }

    this.commandSubscription = this.yamcs.yamcsClient.createCommandSubscription({
      instance: this.yamcs.instance!,
      processor: this.yamcs.processor!,
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

      // Continue running entries
      this.checkAckContinueRunning(rec);
    });
  }

  private stackOptionsFormCallback = (result: any) => {
    if (!this.loaded) {
      return;
    }

    this.advancement = {
      acknowledgment: result.advancementAckDropDown !== "custom" ? result.advancementAckDropDown : result.advancementAckCustom,
      wait: result.advancementWait ?? 0,
    };

    if (result.advancementAckDropDown !== "custom") {
      this.stackOptionsForm.patchValue({ 'advancementAckCustom': undefined }, { emitEvent: false });
    }

    this.dirty$.next(true);
  };

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
    for (const segment of url) {
      if (objectName) {
        objectName += '/';
      }
      objectName += segment.path;
    }
    return objectName;
  }

  private async loadFile(objectName: string) {
    this.objectName = objectName;
    const idx = this.objectName.lastIndexOf('/');
    if (idx === -1) {
      this.folderLink = '/commanding/stacks/browse/';
      this.filename = this.objectName;
    } else {
      const folderName = this.objectName.substring(0, idx);
      this.folderLink = '/commanding/stacks/browse/' + folderName;
      this.filename = this.objectName.substring(idx + 1);
    }

    this.title.setTitle(this.filename);

    const format = utils.getExtension(utils.getFilename(objectName))?.toLowerCase();
    if (format === "ycs" || format === "xml") {
      this.format = format;
    } else {
      this.loaded = true;
      return;
    }

    const response = await this.storageClient.getObject(this.bucket, objectName).catch(err => {
      if (err.statusCode === 404 && format === "xml") {
        this.router.navigate([objectName.replace(/\.xml$/i, ".ycs")], {
          queryParamsHandling: 'preserve',
          relativeTo: this.route.parent,
        }).then(() => this.initStackFile());
      } else {
        this.messageService.showError("Failed to load stack file '" + objectName + "'");
      }
    });

    if (response?.ok) {
      const text = await response.text();
      let entries;
      switch (this.format) {
        case "ycs":
          [entries, this.advancement] = this.parseJSON(text);

          const match = this.ackOptions.find(el => el.id === this.advancement.acknowledgment);
          const ackDefault = match ? match.id : 'custom';

          this.stackOptionsForm.setValue({
            advancementAckDropDown: ackDefault,
            advancementAckCustom: ackDefault === "custom" ? this.advancement.acknowledgment : '',
            advancementWait: this.advancement.wait,
          });
          break;
        case "xml":
          const xmlParser = new DOMParser();
          const doc = xmlParser.parseFromString(text, 'text/xml') as XMLDocument;
          entries = StackFileComponent.parseXML(doc.documentElement, this.configService.getCommandOptions());
          break;
      }

      // Enrich entries with MDB info, it's used in the detail panel
      const promises = [];
      const instance = this.yamcs.instance!;
      for (const entry of entries) {
        const namespace = entry.namespace ?? null;
        const name = entry.name;
        promises.push(this.yamcs.yamcsClient.getCommandForNamespace(instance, namespace, name).then(command => {
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

      // Convert enum values to labels. This provides some resilience to MDB changes
      // where a numeric parameter becomes an enumeration.
      for (const entry of entries) {
        if (entry.command) {
          for (const argumentName in entry.args) {
            const argument = this.getArgument(argumentName, entry.command);
            if (argument?.type.engType === 'enumeration') {
              let match = false;
              for (const enumValue of argument.type.enumValue || []) {
                if (enumValue.label === entry.args[argumentName]) {
                  match = true;
                  break;
                }
              }
              if (!match) {
                for (const enumValue of argument.type.enumValue || []) {
                  if (String(enumValue.value) === String(entry.args[argumentName])) {
                    entry.args[argumentName] = enumValue.label;
                    match = true;
                    break;
                  }
                }
              }
            }
          }
        }
      }

      // Convert arrays/aggregates from JSON to JavaScript
      if (this.format === 'xml') {
        for (const entry of entries) {
          if (entry.command) {
            for (const argumentName in entry.args) {
              if (this.isComplex(argumentName, entry.command)) {
                entry.args[argumentName] = JSON.parse(entry.args[argumentName]);
              }
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

  private getArgument(argumentName: string, info: Command): Argument | null {
    for (const argument of (info.argument || [])) {
      if (argument.name === argumentName) {
        return argument;
      }
    }
    if (info.baseCommand) {
      return this.getArgument(argumentName, info.baseCommand);
    } else {
      return null;
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

    let advancement: AdvancementParams = {
      acknowledgment: stack.advancement?.acknowledgment || "Acknowledge_Queued",
      wait: stack.advancement?.wait != null ? stack.advancement?.wait : 0
    };

    return [entries, advancement] as const;
  }

  private parseJSONentry(command: any) {
    const entry: StackEntry = {
      name: command.name,
      namespace: command.namespace,
      ...(command.comment && { comment: command.comment }),
      ...(command.extraOptions && { extra: this.parseJSONextraOptions(command.extraOptions) }),
      ...(command.arguments && { args: this.parseJSONcommandArguments(command.arguments) }),
      ...(command.advancement && { advancement: command.advancement })
    };
    return entry;
  }

  private parseJSONextraOptions(options: Array<any>) {
    const extra: { [key: string]: Value; } = {};
    for (let option of options) {
      const value = StackFileComponent.convertOptionStringToValue(option.id, (new String(option.value)).toString(), this.configService.getCommandOptions()); // TODO: simplify value conversion
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

  static parseXML(root: Node, commandOptions: any) {
    const entries: StackEntry[] = [];
    for (let i = 0; i < root.childNodes.length; i++) {
      const child = root.childNodes[i];
      if (child.nodeType !== 3) { // Ignore text or whitespace
        if (child.nodeName === 'command') {
          const entry = this.parseXMLEntry(child as Element, commandOptions);
          entries.push(entry);
        }
      }
    }

    return entries;
  }

  private static parseXMLEntry(node: Element, commandOptions: any): StackEntry {
    const args: { [key: string]: any; } = {};
    const extra: { [key: string]: Value; } = {};
    for (let i = 0; i < node.childNodes.length; i++) {
      const child = node.childNodes[i] as Element;
      if (child.nodeName === 'commandArgument') {
        const argumentName = this.getStringAttribute(child, 'argumentName');
        args[argumentName] = this.getStringAttribute(child, 'argumentValue');
        if (args[argumentName] === 'true') {
          args[argumentName] = true;
        } else if (args[argumentName] === 'false') {
          args[argumentName] = false;
        }
      } else if (child.nodeName === 'extraOptions') {
        for (let j = 0; j < child.childNodes.length; j++) {
          const extraChild = child.childNodes[j] as Element;
          if (extraChild.nodeName === 'extraOption') {
            const id = this.getStringAttribute(extraChild, 'id');
            const stringValue = this.getStringAttribute(extraChild, 'value');
            const value = this.convertOptionStringToValue(id, stringValue, commandOptions);
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
      executing: false,
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

  async runSelection() {
    const entry = this.selectedEntry$.value;
    if (!entry) {
      return;
    }
    this.running$.next(true);

    this.runEntry(entry);
  }

  private async runEntry(entry: StackEntry) {
    const executionNumber = ++this.executionCounter;
    const { instance, processor } = this.yamcs;
    const namespace = entry.namespace ?? null;
    return this.yamcs.yamcsClient.issueCommandForNamespace(instance!, processor!, namespace, entry.name, {
      sequenceNumber: executionNumber,
      args: entry.args,
      extra: entry.extra,
    }).then(response => {
      entry.executionNumber = executionNumber;
      entry.executing = true;
      entry.id = response.id;

      // It's possible the WebSocket received data before we
      // get our response.
      const rec = this.commandHistoryRecords.get(entry.id);
      if (rec) {
        entry.record = rec;
        this.checkAckContinueRunning(rec);
      }
    }).catch(err => {
      entry.executionNumber = executionNumber;
      entry.executing = false;
      entry.err = err.message || err;
      this.stopRun();
    }).finally(() => {
      // Refresh subject, to be sure
      this.entries$.next([...this.entries$.value]);
    });
  }

  runFromSelection() {
    let entry = this.selectedEntry$.value;
    if (!entry) {
      this.stopRun();
      return;
    }

    this.running$.next(true);
    this.runningStack = true;
    this.nextCommandScheduled = false;

    this.runEntry(entry);
  }

  private checkAckContinueRunning(record: CommandHistoryRecord) {
    // Attempts to continue the current run from selected by checking acknowledgement statuses
    if (!this.running$.value || this.nextCommandScheduled) {
      return;
    }

    let selectedEntry = this.selectedEntry$.value;

    let acceptingAck = selectedEntry?.advancement?.acknowledgment || this.advancement.acknowledgment!;
    let delay = selectedEntry?.advancement?.wait ?? this.advancement.wait!;

    if (selectedEntry?.id === record.id) {
      let ack = record.acksByName[acceptingAck];
      if (ack && ack.status) {
        if (this.acceptingAckValues.includes(ack.status)) {
          this.nextCommandScheduled = this.runningStack;
          this.nextCommandDelayTimeout = window.setTimeout(() => {
            selectedEntry!.executing = false;
            // Continue execution
            if (this.running$.value && selectedEntry) {
              this.advanceSelection(selectedEntry);
              if (this.runningStack) {
                this.runFromSelection();
              } else {
                this.stopRun();
              }
            }
          }, Math.max(delay, 0));
        } else if (this.stoppingAckValues.includes(ack.status)) {
          selectedEntry.executing = false;
          // Stop exection
          this.stopRun();
        } // Ignore others
      }
    }
  }

  stopRun() {
    this.running$.next(false);
    this.runningStack = false;
    for (const entry of this.entries$.value) {
      entry.executing = false;
    }
    window.clearTimeout(this.nextCommandDelayTimeout);
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

  private static convertOptionStringToValue(id: string, value: string, commandOptions: any): Value | null {
    for (const option of commandOptions) {
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
    const dialogRef = this.dialog.open(EditStackEntryDialogComponent, {
      width: '70%',
      height: '100%',
      autoFocus: false,
      position: {
        right: '0',
      },
      panelClass: 'dialog-full-size',
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
          ...(result.stackOptions.advancement && { advancement: result.stackOptions.advancement })
        };

        // Save with an alias, if so configured and the alias is available
        const config = this.configService.getConfig();
        if (config.preferredNamespace) {
          for (const alias of result.command.alias || []) {
            if (alias.namespace === config.preferredNamespace) {
              entry.name = alias.name;
              entry.namespace = alias.namespace;
              break;
            }
          }
        }

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

    const dialogRef = this.dialog.open(EditStackEntryDialogComponent, {
      width: '70%',
      height: '100%',
      autoFocus: false,
      position: {
        right: '0',
      },
      panelClass: 'dialog-full-size',
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
          ...(result.stackOptions.advancement && { advancement: result.stackOptions.advancement })
        };

        // Save with an alias, if so configured and the alias is available
        const config = this.configService.getConfig();
        if (config.preferredNamespace) {
          for (const alias of result.command.alias || []) {
            if (alias.namespace === config.preferredNamespace) {
              changedEntry.name = alias.name;
              changedEntry.namespace = alias.namespace;
              break;
            }
          }
        }

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
        namespace: entry.namespace,
        args: { ...entry.args },
        comment: entry.comment,
        command: entry.command,
        executing: false,
      };
      if (entry.extra) {
        copiedEntry.extra = { ...entry.extra };
      }
      if (entry.advancement) {
        copiedEntry.advancement = { ...entry.advancement };
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

  showSchedule() {
    const capabilities = this.yamcs.connectionInfo$.value?.instance?.capabilities || [];
    return capabilities.indexOf('timeline') !== -1
      && capabilities.indexOf('activities') !== -1
      && this.authService.getUser()!.hasSystemPrivilege('ControlTimeline');
  }

  openScheduleStackDialog() {
    this.dialog.open(ScheduleStackDialogComponent, {
      width: '600px',
    }).afterClosed().subscribe(scheduleOptions => {
      if (scheduleOptions) {
        const options: CreateTimelineItemRequest = {
          type: 'ACTIVITY',
          duration: '0s',
          name: this.filename,
          start: scheduleOptions['executionTime'],
          tags: scheduleOptions['tags'],
          activityDefinition: {
            "type": "COMMAND_STACK",
            "args": {
              "processor": this.yamcs.processor!,
              "bucket": this.bucket,
              "stack": this.filename,
            }
          },
        };

        this.yamcs.yamcsClient.createTimelineItem(this.yamcs.instance!, options)
          .then(() => {
            this.messageService.showInfo('Command stack scheduled');
          })
          .catch(err => this.messageService.showError(err));
      }
    });
  }

  saveStack() {
    let file;
    switch (this.format) {
      case "ycs":
        file = new StackFormatter(this.entries$.value, { advancement: this.advancement }).toJSON();
        break;
      case "xml":
        file = new StackFormatter(this.entries$.value, { advancement: this.advancement }).toXML();
        break;
    }
    const type = (this.format === 'xml' ? 'application/xml' : 'application/json');
    const blob = new Blob([file], { type });
    return this.storageClient.uploadObject(this.bucket, this.objectName, blob).then(() => {
      this.dirty$.next(false);
    });
  }

  setExportURLs() {
    const formatter = new StackFormatter(this.entries$.value, { advancement: this.advancement });

    const JSONblob = new Blob([formatter.toJSON()], { type: 'application/json' });
    this.JSONblobURL = this.sanitizer.bypassSecurityTrustResourceUrl(URL.createObjectURL(JSONblob));

    const XMLblob = new Blob([formatter.toXML()], { type: 'application/xml' });
    this.XMLblobURL = this.sanitizer.bypassSecurityTrustResourceUrl(URL.createObjectURL(XMLblob));
  }

  private static getStringAttribute(node: Node, name: string) {
    const attr = (node as Element).attributes.getNamedItem(name);
    if (attr === null) {
      throw new Error(`No attribute named ${name}`);
    } else {
      return attr.textContent || '';
    }
  }

  async convertToJSON() {
    if (this.converting) {
      return;
    }

    if (confirm(`Are you sure you want to convert '${this.objectName}' to the new format?\nThis will delete the original XML file.`)) {
      this.converting = true;

      if (this.dirty$.value) {
        await this.saveStack();
      }

      StackFileComponent.convertToJSON(this.messageService, this.storageClient, this.bucket, this.objectName, this.entries$.value, { advancement: this.advancement })
        .then((jsonObjectName) => {
          this.router.navigate([jsonObjectName + ".ycs"], { queryParamsHandling: 'preserve', relativeTo: this.route.parent })
            .then(() => this.initStackFile());
        }).finally(() => this.converting = false);
    }
  }

  static async convertToJSON(
    messageService: MessageService,
    storageClient: StorageClient,
    bucket: string,
    objectName: string,
    entries: StackEntry[],
    stackOptions: { advancement?: AdvancementParams; }
  ) {
    const formatter = new StackFormatter(entries, stackOptions);
    const blob = new Blob([formatter.toJSON()], { type: 'application/json' });
    const jsonObjectName = utils.getBasename(objectName);

    if (!jsonObjectName) {
      messageService.showError("Failed to convert command stack");
      console.error("Failed to convert command stack due to objectName");
      return;
    }
    await storageClient.uploadObject(bucket, jsonObjectName + ".ycs", blob);
    await storageClient.deleteObject(bucket, objectName);
    return jsonObjectName;
  }

  ngOnDestroy() {
    this.commandSubscription?.cancel();
    this.stackOptionsForm.reset();
  }
}
