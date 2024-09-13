import { CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { FormBuilder, UntypedFormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { DomSanitizer, SafeResourceUrl, Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { AcknowledgmentInfo, AdvancementParams, Argument, Command, CommandHistoryRecord, CommandStep, CommandSubscription, ConfigService, CreateTimelineItemRequest, MessageService, NamedObjectId, ParameterSubscription, ParameterValue, StackFormatter, Step, StorageClient, WebappSdkModule, YaSelectOption, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { map } from 'rxjs/operators';
import { ExtraAcknowledgmentsTableComponent } from '../../../commanding/command-history/extra-acknowledgments-table/extra-acknowledgments-table.component';
import { YamcsAcknowledgmentsTableComponent } from '../../../commanding/command-history/yamcs-acknowledgments-table/yamcs-acknowledgments-table.component';
import { AuthService } from '../../../core/services/AuthService';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { AdvanceAckHelpComponent } from '../advance-ack-help/advance-ack-help.component';
import { EditCheckEntryDialogComponent } from '../edit-check-entry-dialog/edit-check-entry-dialog.component';
import { CommandResult, EditCommandEntryDialogComponent } from '../edit-command-entry-dialog/edit-command-entry-dialog.component';
import { ScheduleStackDialogComponent } from '../schedule-stack-dialog/schedule-stack-dialog.component';
import { StackedCheckEntryComponent } from '../stacked-check-entry/stacked-check-entry.component';
import { StackedCommandDetailComponent } from '../stacked-command-detail/stacked-command-detail.component';
import { StackedCommandEntryComponent } from '../stacked-command-entry/stacked-command-entry.component';
import { NamedParameterValue, StackedCheckEntry, StackedCommandEntry, StackedEntry } from './StackedEntry';
import { parseXML } from './xmlparse';
import { parseYCS } from './ycsparse';

const ACK_CONTINUE = ['OK', 'DISABLED'];
const ACK_STOP = ['NOK', 'CANCELLED'];

@Component({
  standalone: true,
  templateUrl: './stack-file.component.html',
  styleUrl: './stack-file.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdvanceAckHelpComponent,
    ExtraAcknowledgmentsTableComponent,
    InstancePageTemplateComponent,
    InstanceToolbarComponent,
    MarkdownComponent,
    StackedCheckEntryComponent,
    StackedCommandDetailComponent,
    StackedCommandEntryComponent,
    WebappSdkModule,
    YamcsAcknowledgmentsTableComponent,
  ],
})
export class StackFileComponent implements OnDestroy {
  private storageClient: StorageClient;

  objectName: string;
  filename: string;
  folderLink: string;

  dirty$ = new BehaviorSubject<boolean>(false);
  entries$ = new BehaviorSubject<StackedEntry[]>([]);
  hasOutputs$ = this.entries$.pipe(
    map(entries => {
      for (const entry of entries) {
        if (entry.hasOutputs()) {
          return true;
        }
      }
      return false;
    })
  );

  running$ = new BehaviorSubject<boolean>(false);
  runningStack = false;

  private parameterSubscription: ParameterSubscription;
  private commandSubscription: CommandSubscription;
  selectedEntry$ = new BehaviorSubject<StackedEntry | null>(null);
  clipboardEntry$ = new BehaviorSubject<StackedEntry | null>(null);
  private commandHistoryRecords = new Map<string, CommandHistoryRecord>();

  @ViewChild('entryParent')
  private entryParent: ElementRef<HTMLDivElement>;

  private executionCounter = 0;

  private bucket: string;

  loaded = false;
  format: 'ycs' | 'xml';

  jsonBlobUrl: SafeResourceUrl;
  xmlBlobUrl: SafeResourceUrl;

  converting = false;

  // Configuration of command ack to accept to execute the following
  private advancement: AdvancementParams = {
    acknowledgment: 'Acknowledge_Queued',
    wait: 0,
  };

  private nextCommandDelayTimeout: number;
  private nextEntryScheduled = false;

  stackOptionsForm: UntypedFormGroup;
  extraAcknowledgments: AcknowledgmentInfo[];
  ackOptions: YaSelectOption[] = [
    { id: 'Acknowledge_Queued', label: 'Queued' },
    { id: 'Acknowledge_Released', label: 'Released' },
    { id: 'Acknowledge_Sent', label: 'Sent' },
    { id: 'CommandComplete', label: 'Completed' },
  ];

  idMapping: { [key: number]: NamedObjectId; } = {};
  pvals$ = new BehaviorSubject<{ [key: string]: ParameterValue; }>({});

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

    this.parameterSubscription = yamcs.yamcsClient.createParameterSubscription({
      instance: yamcs.instance!,
      processor: yamcs.processor!,
      action: 'REPLACE',
      abortOnInvalid: false,
      sendFromCache: true,
      updateOnExpiration: true,
      id: [],
    }, data => {
      if (data.mapping) {
        this.idMapping = data.mapping;
      }
      if (data.values) {
        const pvals = { ...this.pvals$.value };
        for (const value of data.values) {
          const pname = this.idMapping[value.numericId];
          if (pname) {
            pvals[pname.name] = value;
          }
        }
        this.pvals$.next(pvals);
      }
    });

    this.initStackFile();
  }

  private initStackFile() {
    const initialObject = this.getObjectNameFromUrl();

    this.loadFile(initialObject);

    if (this.format !== 'ycs') {
      this.stackOptionsForm.disable();
    } else {
      this.stackOptionsForm.enable();
      this.stackOptionsForm.valueChanges.subscribe(this.stackOptionsFormCallback);
    }

    this.commandSubscription = this.yamcs.yamcsClient.createCommandSubscription({
      instance: this.yamcs.instance!,
      processor: this.yamcs.processor!,
      ignorePastCommands: true,
    }, histUpdate => {
      const id = histUpdate.id;
      let rec = this.commandHistoryRecords.get(id);
      if (rec) {
        rec = rec.mergeEntry(histUpdate);
      } else {
        rec = new CommandHistoryRecord(histUpdate);
      }
      this.commandHistoryRecords.set(id, rec);

      // Try to find a with matching id. If it's there
      // update it. If not: no problem, the link will be made when
      // handling the command response.
      for (const entry of this.entries$.value) {
        if ((entry instanceof StackedCommandEntry) && entry.id === id) {
          entry.record = rec;
          break;
        }
      }

      // Continue running entries
      const currentEntry = this.selectedEntry$.value;
      if (currentEntry && (currentEntry instanceof StackedCommandEntry)) {
        if (currentEntry instanceof StackedCommandEntry) {
          this.checkAckContinueRunning(currentEntry, rec);
        }
      }
    });
  }

  private stackOptionsFormCallback = (result: any) => {
    if (!this.loaded) {
      return;
    }

    this.advancement = {
      acknowledgment: result.advancementAckDropDown !== 'custom'
        ? result.advancementAckDropDown : result.advancementAckCustom,
      wait: result.advancementWait ?? 0,
    };

    if (result.advancementAckDropDown !== 'custom') {
      this.stackOptionsForm.patchValue({
        'advancementAckCustom': undefined,
      }, { emitEvent: false });
    }

    this.dirty$.next(true);
  };

  handleDrop(event: CdkDragDrop<Step[]>) {
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
      this.folderLink = '/procedures/stacks/browse/';
      this.filename = this.objectName;
    } else {
      const folderName = this.objectName.substring(0, idx);
      this.folderLink = '/procedures/stacks/browse/' + folderName;
      this.filename = this.objectName.substring(idx + 1);
    }

    this.title.setTitle(this.filename);

    const format = utils.getExtension(utils.getFilename(objectName))?.toLowerCase();
    if (format === 'ycs' || format === 'xml') {
      this.format = format;
    } else {
      this.loaded = true;
      return;
    }

    const response = await this.storageClient.getObject(this.bucket, objectName).catch(err => {
      if (err.statusCode === 404 && format === 'xml') {
        this.router.navigate([objectName.replace(/\.xml$/i, '.ycs')], {
          queryParamsHandling: 'preserve',
          relativeTo: this.route.parent,
        }).then(() => this.initStackFile());
      } else {
        this.messageService.showError(`Failed to load stack file: ${objectName}`);
      }
    });

    if (response?.ok) {
      const text = await response.text();
      const entries: StackedEntry[] = [];

      switch (this.format) {
        case 'ycs':
          let ycsEntries: Step[];
          [ycsEntries, this.advancement] = parseYCS(text, this.configService.getCommandOptions());
          for (const ycsEntry of ycsEntries) {
            if (ycsEntry.type === 'check') {
              entries.push(new StackedCheckEntry(ycsEntry));
            } else if (ycsEntry.type === 'command') {
              entries.push(new StackedCommandEntry(ycsEntry));
            }
          }

          const match = this.ackOptions.find(el => el.id === this.advancement.acknowledgment);
          const ackDefault = match ? match.id : 'custom';

          this.stackOptionsForm.setValue({
            advancementAckDropDown: ackDefault,
            advancementAckCustom: ackDefault === 'custom' ? this.advancement.acknowledgment : '',
            advancementWait: this.advancement.wait,
          });
          break;
        case 'xml':
          const xmlModels = parseXML(text, this.configService.getCommandOptions());
          for (const xmlModel of xmlModels) {
            entries.push(new StackedCommandEntry(xmlModel));
          }
          break;
      }

      const commandEntries = entries.filter(entry => entry instanceof StackedCommandEntry);

      // Enrich entries with MDB info, it's used in the detail panel
      const promises = [];
      const instance = this.yamcs.instance!;
      for (const entry of commandEntries) {
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
      for (const entry of commandEntries) {
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
        for (const entry of commandEntries) {
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
      this.updateParameterSubscription();
      this.loaded = true;
    }
  }

  private updateParameterSubscription() {
    const parameters = new Set<string>();
    for (const entry of this.entries$.value) {
      if (entry.type === 'check') {
        for (const check of entry.parameters) {
          parameters.add(check.parameter);
        }
      }
    }

    const ids: NamedObjectId[] = [];
    parameters.forEach(p => ids.push({ 'name': p }));

    this.parameterSubscription.sendMessage({
      action: 'REPLACE',
      instance: this.yamcs.instance!,
      processor: this.yamcs.processor!,
      abortOnInvalid: false,
      sendFromCache: true,
      updateOnExpiration: true,
      id: ids,
    });
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

  selectEntry(entry: StackedEntry) {
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
    entry?.clearOutputs();
  }

  clearOutputs() {
    for (const entry of this.entries$.value) {
      entry.clearOutputs();
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

  private async runEntry(entry: StackedEntry) {
    const executionNumber = ++this.executionCounter;
    const { instance, processor } = this.yamcs;
    if (entry instanceof StackedCommandEntry) {
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
          this.checkAckContinueRunning(entry, rec);
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
    } else if (entry instanceof StackedCheckEntry) {
      const pvals: NamedParameterValue[] = [];
      for (const check of entry.parameters) {
        const allPvals = this.pvals$.value;
        pvals.push({
          parameter: check.parameter,
          pval: allPvals[check.parameter] || null,
        });
      }
      entry.pvals = pvals;
      entry.executionNumber = executionNumber;
      this.entries$.next([...this.entries$.value]); // Refresh subject
      this.continueRunning(entry);
    }
  }

  runFromSelection() {
    let entry = this.selectedEntry$.value;
    if (!entry) {
      this.stopRun();
      return;
    }

    this.running$.next(true);
    this.runningStack = true;
    this.nextEntryScheduled = false;

    this.runEntry(entry);
  }

  private checkAckContinueRunning(entry: StackedCommandEntry, record: CommandHistoryRecord) {
    // Attempts to continue the current run from selected by checking acknowledgement statuses
    if (!this.running$.value || this.nextEntryScheduled) {
      return;
    }

    let acceptingAck = entry.advancement?.acknowledgment || this.advancement.acknowledgment!;
    let delay = entry.advancement?.wait ?? this.advancement.wait!;

    if (entry.id === record.id) {
      let ack = record.acksByName[acceptingAck];
      if (ack && ack.status) {
        if (ACK_CONTINUE.includes(ack.status)) {
          this.continueRunning(entry, delay);
        } else if (ACK_STOP.includes(ack.status)) {
          entry.executing = false;
          // Stop execution
          this.stopRun();
        }
      }
    }
  }

  private continueRunning(entry: StackedEntry, delay = 0) {
    this.nextEntryScheduled = this.runningStack;
    this.nextCommandDelayTimeout = window.setTimeout(() => {
      entry.executing = false;
      if (this.running$.value) {
        this.advanceSelection(entry);
        if (this.runningStack) {
          this.runFromSelection();
        } else {
          this.stopRun();
        }
      }
    }, Math.max(delay, 0));
  }

  stopRun() {
    this.running$.next(false);
    this.runningStack = false;
    for (const entry of this.entries$.value) {
      entry.executing = false;
    }
    window.clearTimeout(this.nextCommandDelayTimeout);
    this.nextEntryScheduled = false;
  }

  private advanceSelection(entry: StackedEntry) {
    const entries = this.entries$.value;
    const idx = entries.indexOf(entry);
    if (idx < entries.length - 1) {
      const nextEntry = entries[idx + 1];
      this.selectEntry(nextEntry);

      const parentEl = this.entryParent.nativeElement;
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

  addCheckEntry() {
    this.dialog.open(EditCheckEntryDialogComponent, {
      autoFocus: false,
      width: '800px',
      data: {
        edit: false,
      },
    }).afterClosed().subscribe((result?: any) => {
      if (result) {
        const entry = new StackedCheckEntry({
          type: 'check',
          ...result,
        });

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
        this.updateParameterSubscription();
      }
    });
  }

  addCommandEntry() {
    const dialogRef = this.dialog.open(EditCommandEntryDialogComponent, {
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
        const model: CommandStep = {
          type: 'command',
          name: result.command.qualifiedName,
          args: result.args,
          comment: result.comment,
          extra: result.extra,
        };

        if (result.stackOptions.advancement) {
          model.advancement = result.stackOptions.advancement;
        }

        // Save with an alias, if so configured and the alias is available
        const config = this.configService.getConfig();
        if (config.preferredNamespace) {
          for (const alias of result.command.alias || []) {
            if (alias.namespace === config.preferredNamespace) {
              model.name = alias.name;
              model.namespace = alias.namespace;
              break;
            }
          }
        }

        const entry = new StackedCommandEntry(model);
        entry.command = result.command;
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

  editSelectedEntry() {
    const entry = this.selectedEntry$.value!;
    this.editEntry(entry);
  }

  editEntry(entry: StackedEntry) {
    this.selectEntry(entry);

    if (entry instanceof StackedCommandEntry) {
      this.dialog.open(EditCommandEntryDialogComponent, {
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
          format: this.format,
        }
      }).afterClosed().subscribe((result?: CommandResult) => {
        if (result) {
          const changedModel: CommandStep = {
            type: 'command',
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
                changedModel.name = alias.name;
                changedModel.namespace = alias.namespace;
                break;
              }
            }
          }

          const changedEntry = new StackedCommandEntry(changedModel);

          const entries = this.entries$.value;
          const idx = entries.indexOf(entry);
          entries.splice(idx, 1, changedEntry);
          this.entries$.next([...this.entries$.value]);

          this.selectEntry(changedEntry);
          this.dirty$.next(true);
        }
      });
    } else if (entry instanceof StackedCheckEntry) {
      this.dialog.open(EditCheckEntryDialogComponent, {
        autoFocus: false,
        width: '800px',
        data: {
          edit: true,
          entry,
        }
      }).afterClosed().subscribe((result?: any) => {
        if (result) {
          const changedEntry = new StackedCheckEntry({
            type: 'check',
            ...result,
          });

          const entries = this.entries$.value;
          const idx = entries.indexOf(entry);
          entries.splice(idx, 1, changedEntry);
          this.entries$.next([...this.entries$.value]);

          this.selectEntry(changedEntry);
          this.dirty$.next(true);
          this.updateParameterSubscription();
        }
      });
    }

    return false;
  }

  cutSelectedEntry() {
    const entry = this.selectedEntry$.value!;
    this.advanceSelection(entry);
    this.clipboardEntry$.next(entry);

    const entries = this.entries$.value;
    const idx = entries.indexOf(entry);
    entries.splice(idx, 1);
    this.entries$.next([...this.entries$.value]);

    this.dirty$.next(true);
  }

  copySelectedEntry() {
    const entry = this.selectedEntry$.value!;
    this.clipboardEntry$.next(entry);
  }

  pasteEntry() {
    const entry = this.clipboardEntry$.value;
    if (entry) {
      const copiedEntry = entry.copy();

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
            'type': 'COMMAND_STACK',
            'args': {
              'processor': this.yamcs.processor!,
              'bucket': this.bucket,
              'stack': this.filename,
            }
          },
        };

        this.yamcs.yamcsClient.createTimelineItem(this.yamcs.instance!, options)
          .then(() => {
            this.messageService.showInfo('Stack scheduled');
          })
          .catch(err => this.messageService.showError(err));
      }
    });
  }

  saveStack() {
    const modelEntries = this.entries$.value.map(e => e.model);
    let file;
    switch (this.format) {
      case 'ycs':
        file = new StackFormatter(modelEntries, { advancement: this.advancement }).toJSON();
        break;
      case 'xml':
        file = new StackFormatter(modelEntries, { advancement: this.advancement }).toXML();
        break;
    }
    const type = (this.format === 'xml') ? 'application/xml' : 'application/json';
    const blob = new Blob([file], { type });
    return this.storageClient.uploadObject(this.bucket, this.objectName, blob).then(() => {
      this.dirty$.next(false);
    });
  }

  setExportURLs() {
    const entryModels = this.entries$.value.map(e => e.model);
    const formatter = new StackFormatter(entryModels, { advancement: this.advancement });

    const jsonBlob = new Blob([formatter.toJSON()], { type: 'application/json' });
    this.jsonBlobUrl = this.sanitizer.bypassSecurityTrustResourceUrl(URL.createObjectURL(jsonBlob));

    const xmlBlob = new Blob([formatter.toXML()], { type: 'application/xml' });
    this.xmlBlobUrl = this.sanitizer.bypassSecurityTrustResourceUrl(URL.createObjectURL(xmlBlob));
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

      const entryModels = this.entries$.value.map(e => e.model);
      StackFileComponent.convertToJSON(
        this.messageService,
        this.storageClient,
        this.bucket,
        this.objectName,
        entryModels,
        { advancement: this.advancement },
      ).then((jsonObjectName) => {
        this.router.navigate([jsonObjectName + '.ycs'], {
          queryParamsHandling: 'preserve',
          relativeTo: this.route.parent,
        }).then(() => this.initStackFile());
      }).finally(() => this.converting = false);
    }
  }

  static async convertToJSON(
    messageService: MessageService,
    storageClient: StorageClient,
    bucket: string,
    objectName: string,
    entries: Step[],
    stackOptions: { advancement?: AdvancementParams; }
  ) {
    const formatter = new StackFormatter(entries, stackOptions);
    const blob = new Blob([formatter.toJSON()], { type: 'application/json' });
    const jsonObjectName = utils.getBasename(objectName);

    if (!jsonObjectName) {
      messageService.showError('Failed to convert stack');
      console.error('Failed to convert stack due to objectName');
      return;
    }
    await storageClient.uploadObject(bucket, jsonObjectName + '.ycs', blob);
    await storageClient.deleteObject(bucket, objectName);
    return jsonObjectName;
  }

  ngOnDestroy() {
    this.parameterSubscription?.cancel();
    this.commandSubscription?.cancel();
    this.stackOptionsForm.reset();
  }
}
