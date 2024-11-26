import { CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, ElementRef, Input, OnDestroy, OnInit, SecurityContext, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { DomSanitizer, SafeResourceUrl, Title } from '@angular/platform-browser';
import { AppearanceService, CommandHistoryRecord, CommandStep, CommandSubscription, ConfigService, CreateTimelineItemRequest, MessageService, NamedObjectId, ParameterSubscription, ParameterValue, StackFormatter, Step, utils, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, delay, filter, first, Observable, Subscription, timeout, TimeoutError } from 'rxjs';
import { ExtraAcknowledgmentsTableComponent } from '../../../commanding/command-history/extra-acknowledgments-table/extra-acknowledgments-table.component';
import { YamcsAcknowledgmentsTableComponent } from '../../../commanding/command-history/yamcs-acknowledgments-table/yamcs-acknowledgments-table.component';
import { AuthService } from '../../../core/services/AuthService';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { AdvanceAckHelpComponent } from '../advance-ack-help/advance-ack-help.component';
import { EditCheckEntryDialogComponent } from '../edit-check-entry-dialog/edit-check-entry-dialog.component';
import { CommandResult, EditCommandEntryDialogComponent } from '../edit-command-entry-dialog/edit-command-entry-dialog.component';
import { EditTextEntryDialogComponent } from '../edit-text-entry-dialog/edit-text-entry-dialog.component';
import { EditVerifyEntryDialogComponent } from '../edit-verify-entry-dialog/edit-verify-entry-dialog.component';
import { ScheduleStackDialogComponent } from '../schedule-stack-dialog/schedule-stack-dialog.component';
import { StackFilePageTabsComponent } from '../stack-file-page-tabs/stack-file-page-tabs.component';
import { StackedCheckEntryComponent } from '../stacked-check-entry/stacked-check-entry.component';
import { StackedCommandDetailComponent } from '../stacked-command-detail/stacked-command-detail.component';
import { StackedCommandEntryComponent } from '../stacked-command-entry/stacked-command-entry.component';
import { StackedTextEntryComponent } from '../stacked-text-entry/stacked-text-entry.component';
import { StackedVerifyEntryComponent } from '../stacked-verify-entry/stacked-verify-entry.component';
import { VerifyTableComponent } from '../verify-table/verify-table.component';
import { NamedParameterValue, StackedCheckEntry, StackedCommandEntry, StackedEntry, StackedTextEntry, StackedVerifyEntry } from './StackedEntry';
import { StackFileService } from './StackFileService';

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
    StackedTextEntryComponent,
    StackedVerifyEntryComponent,
    StackFilePageTabsComponent,
    VerifyTableComponent,
    WebappSdkModule,
    YamcsAcknowledgmentsTableComponent,
  ],
})
export class StackFileComponent implements OnInit, OnDestroy {

  @Input()
  objectName: string;

  filename: string;
  folderLink: string;

  running$ = new BehaviorSubject<boolean>(false);
  runningStack = false;

  private parameterSubscription: ParameterSubscription;
  private commandSubscription: CommandSubscription;
  private stepScopedSubscriptions: Subscription[] = [];
  entries$: BehaviorSubject<StackedEntry[]>;
  hasState$: Observable<boolean>;
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

  private nextCommandDelayTimeout: number;
  private nextEntryScheduled = false;

  idMapping: { [key: number]: NamedObjectId; } = {};
  pvals$ = new BehaviorSubject<{ [key: string]: ParameterValue; }>({});

  constructor(
    private dialog: MatDialog,
    readonly yamcs: YamcsService,
    private title: Title,
    private authService: AuthService,
    private configService: ConfigService,
    private messageService: MessageService,
    private sanitizer: DomSanitizer,
    readonly appearanceService: AppearanceService,
    readonly stackFileService: StackFileService,
  ) {
    this.bucket = configService.getStackBucket();
    this.entries$ = stackFileService.entries$;
    this.hasState$ = stackFileService.hasState$;

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
  }

  ngOnInit(): void {
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

    const format = utils.getExtension(utils.getFilename(this.objectName))?.toLowerCase();
    if (format === 'ycs' || format === 'xml') {
      this.format = format;
    } else {
      this.loaded = true;
      return;
    }

    const entries = this.stackFileService.entries;

    // Update the page
    this.stackFileService.updateEntries(entries);
    this.selectedEntry$.next(entries.length ? entries[0] : null);
    this.updateParameterSubscription();
    this.loaded = true;

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
      for (const entry of this.stackFileService.entries$.value) {
        if ((entry instanceof StackedCommandEntry) && entry.id === id) {
          entry.record = rec;
          break;
        }
      }

      // Continue running entries
      const currentEntry = this.selectedEntry$.value;
      if (currentEntry && (currentEntry instanceof StackedCommandEntry)) {
        this.checkAckContinueRunning(currentEntry, rec);
      }
    });
  }

  handleDrop(event: CdkDragDrop<Step[]>) {
    if (event.previousIndex !== event.currentIndex) {
      moveItemInArray(this.entries$.value, event.previousIndex, event.currentIndex);
      this.stackFileService.updateEntries([...this.entries$.value]);
      this.stackFileService.markDirty();
    }
  }

  private updateParameterSubscription() {
    const parameters = new Set<string>();
    for (const entry of this.stackFileService.entries$.value) {
      if (entry.type === 'check') {
        for (const check of entry.parameters) {
          parameters.add(check.parameter);
        }
      } else if (entry.type === 'verify') {
        for (const comparison of entry.condition) {
          parameters.add(comparison.parameter);
        }
      }
    }

    const ids: NamedObjectId[] = [];
    parameters.forEach(p => ids.push({ 'name': p }));

    // Ensure the initial reply is already received
    this.parameterSubscription.addReplyListener(() => {
      this.parameterSubscription.sendMessage({
        action: 'REPLACE',
        instance: this.yamcs.instance!,
        processor: this.yamcs.processor!,
        abortOnInvalid: false,
        sendFromCache: true,
        updateOnExpiration: true,
        id: ids,
      });
    });
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
        this.selectedEntry$.next(null);
        this.stackFileService.updateEntries([...this.entries$.value]);
        this.stackFileService.markDirty();
      }
    }
  }

  clearOutputs() {
    for (const entry of this.entries$.value) {
      entry.clearOutputs();
    }
    this.executionCounter = 0;
    this.stackFileService.logs$.next([]);
    this.stackFileService.updateEntries([...this.entries$.value]);
    if (this.entries$.value.length) {
      this.selectEntry(this.entries$.value[0]);
    }
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
    entry.clearOutputs();
    if (entry instanceof StackedCommandEntry) {
      const namespace = entry.namespace ?? null;
      return this.yamcs.yamcsClient.issueCommandForNamespace(instance!, processor!, namespace, entry.name, {
        sequenceNumber: executionNumber,
        args: entry.args,
        stream: entry.stream,
        extra: entry.extra,
      }).then(response => {
        entry.executionNumber = executionNumber;
        entry.executing = true;
        entry.id = response.id;

        const logMessage = `Sending command ${entry}`;
        this.stackFileService.addLogEntry(executionNumber, logMessage);

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
        this.stackFileService.updateEntries([...this.entries$.value]);
      });
    } else if (entry instanceof StackedVerifyEntry) {
      entry.executionNumber = executionNumber;
      entry.executing = true;
      await this.runVerifyEntry(entry);
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
      this.stackFileService.updateEntries([...this.entries$.value]); // Refresh subject
      this.continueRunning(entry);
    } else if (entry instanceof StackedTextEntry) {
      entry.executionNumber = executionNumber;
      this.stackFileService.updateEntries([...this.entries$.value]); // Refresh subject
      this.continueRunning(entry);
    }
  }

  private async runVerifyEntry(entry: StackedVerifyEntry) {
    const logMessage = `Verifying ${entry}`;
    this.stackFileService.addLogEntry(entry.executionNumber!, logMessage);

    let pipeline = this.pvals$.pipe();

    if (entry.delay && entry.delay > 0) {
      pipeline = pipeline.pipe(delay(entry.delay));
    }
    pipeline = pipeline.pipe(
      filter(pvals => this.testVerifyEntry(entry)),
      first(),  // Unsubscribe when test succeeds)
    );
    if (entry.timeout && entry.timeout > 0) {
      const delay = Math.max(0, entry.delay || 0);
      pipeline = pipeline.pipe(
        timeout(delay + entry.timeout),
      );
    }
    const subscription = pipeline.subscribe({
      next: () => this.continueRunning(entry),
      error: err => {
        if (err instanceof TimeoutError) {
          entry.executing = false;
          entry.err = err.message || String(err);
          this.stopRun();
        }
      },
    });
    this.stepScopedSubscriptions.push(subscription);
  }

  private testVerifyEntry(entry: StackedVerifyEntry) {
    const result = entry.test(this.pvals$.value);
    this.stackFileService.updateEntries([...this.entries$.value]); // Refresh subject
    return result;
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

    const { advancement: stackAdvancement } = this.stackFileService;

    let acceptingAck = entry.advancement?.acknowledgment || stackAdvancement.acknowledgment!;
    let delay = entry.advancement?.wait ?? stackAdvancement.wait!;

    if (entry.id === record.id) {
      let ack = record.acksByName[acceptingAck];
      if (ack && ack.status) {
        if (ack.status === 'OK' || ack.status === 'DISABLED') {
          this.continueRunning(entry, delay);
        } else if (ack.status === 'NOK' || ack.status === 'CANCELLED') {
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
    this.clearStepScopedSubscriptions();
    this.running$.next(false);
    this.runningStack = false;
    for (const entry of this.stackFileService.entries$.value) {
      entry.executing = false;
      if (entry instanceof StackedVerifyEntry) {
        for (const pval of entry.pvals || []) {
          if (pval?.status && pval.status() === 'pending') {
            pval.status.set('cancelled');
          }
        }
      }
    }
    window.clearTimeout(this.nextCommandDelayTimeout);
    this.nextEntryScheduled = false;
  }

  private clearStepScopedSubscriptions() {
    this.stepScopedSubscriptions.forEach(s => s.unsubscribe());
    this.stepScopedSubscriptions.length = 0;
  }

  private advanceSelection(entry: StackedEntry) {
    const entries = this.stackFileService.entries$.value;
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
          this.stackFileService.updateEntries([...this.entries$.value]);
        } else {
          this.stackFileService.updateEntries([...this.entries$.value, entry]);
        }
        this.selectEntry(entry);
        this.stackFileService.markDirty();
        this.updateParameterSubscription();
      }
    });
  }

  addVerifyEntry() {
    this.dialog.open(EditVerifyEntryDialogComponent, {
      autoFocus: false,
      width: '800px',
      data: {
        edit: false,
      },
    }).afterClosed().subscribe((result?: any) => {
      if (result) {
        const entry = new StackedVerifyEntry({
          type: 'verify',
          ...result,
        });

        const relto = this.selectedEntry$.value;
        if (relto) {
          const entries = this.entries$.value;
          const idx = entries.indexOf(relto);
          entries.splice(idx + 1, 0, entry);
          this.stackFileService.updateEntries([...this.entries$.value]);
        } else {
          this.stackFileService.updateEntries([...this.entries$.value, entry]);
        }
        this.selectEntry(entry);
        this.stackFileService.markDirty();
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
      }
    });

    dialogRef.afterClosed().subscribe((result?: CommandResult) => {
      if (result) {
        const model: CommandStep = {
          type: 'command',
          name: result.command.qualifiedName,
          args: result.args,
          comment: result.comment,
          stream: result.stream,
          extra: result.extra,
        };

        if (result.advancement) {
          model.advancement = result.advancement;
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
          this.stackFileService.updateEntries([...this.entries$.value]);
        } else {
          this.stackFileService.updateEntries([...this.entries$.value, entry]);
        }
        this.selectEntry(entry);
        this.stackFileService.markDirty();
      }
    });
  }

  addTextEntry() {
    this.dialog.open(EditTextEntryDialogComponent, {
      autoFocus: false,
      width: '800px',
      data: {
        edit: false,
      },
    }).afterClosed().subscribe((result?: string) => {
      if (result) {
        const entry = new StackedTextEntry({
          type: 'text',
          text: result,
        });

        const relto = this.selectedEntry$.value;
        if (relto) {
          const entries = this.entries$.value;
          const idx = entries.indexOf(relto);
          entries.splice(idx + 1, 0, entry);
          this.stackFileService.updateEntries([...this.entries$.value]);
        } else {
          this.stackFileService.updateEntries([...this.entries$.value, entry]);
        }
        this.selectEntry(entry);
        this.stackFileService.markDirty();
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
        }
      }).afterClosed().subscribe((result?: CommandResult) => {
        if (result) {
          const changedModel: CommandStep = {
            type: 'command',
            name: result.command.qualifiedName,
            args: result.args,
            comment: result.comment,
            stream: result.stream,
            extra: result.extra,
            advancement: result.advancement,
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
          changedEntry.command = result.command;

          const entries = this.entries$.value;
          const idx = entries.indexOf(entry);
          entries.splice(idx, 1, changedEntry);
          this.stackFileService.updateEntries([...this.entries$.value]);

          this.selectEntry(changedEntry);
          this.stackFileService.markDirty();
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
          this.stackFileService.updateEntries([...this.entries$.value]);

          this.selectEntry(changedEntry);
          this.stackFileService.markDirty();
          this.updateParameterSubscription();
        }
      });
    } else if (entry instanceof StackedTextEntry) {
      this.dialog.open(EditTextEntryDialogComponent, {
        autoFocus: false,
        width: '800px',
        data: {
          edit: true,
          entry,
        }
      }).afterClosed().subscribe((result?: string) => {
        if (result) {
          const changedEntry = new StackedTextEntry({
            type: 'text',
            text: result,
          });

          const entries = this.entries$.value;
          const idx = entries.indexOf(entry);
          entries.splice(idx, 1, changedEntry);
          this.stackFileService.updateEntries([...this.entries$.value]);

          this.selectEntry(changedEntry);
          this.stackFileService.markDirty();
        }
      });
    } else if (entry instanceof StackedVerifyEntry) {
      this.dialog.open(EditVerifyEntryDialogComponent, {
        autoFocus: false,
        width: '800px',
        data: {
          edit: true,
          entry,
        }
      }).afterClosed().subscribe((result?: any) => {
        if (result) {
          const changedEntry = new StackedVerifyEntry({
            type: 'verify',
            ...result,
          });

          const entries = this.entries$.value;
          const idx = entries.indexOf(entry);
          entries.splice(idx, 1, changedEntry);
          this.stackFileService.updateEntries([...this.entries$.value]);

          this.selectEntry(changedEntry);
          this.stackFileService.markDirty();
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
    this.stackFileService.updateEntries([...this.entries$.value]);

    this.stackFileService.markDirty();
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
        this.stackFileService.updateEntries([...this.entries$.value]);
      } else {
        this.stackFileService.updateEntries([...this.entries$.value, copiedEntry]);
      }
      this.selectEntry(copiedEntry);
      this.stackFileService.markDirty();
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

  setExportURLs() {
    const entryModels = this.entries$.value.map(e => e.model);
    const formatter = new StackFormatter(entryModels, {
      advancement: this.stackFileService.advancement,
    });

    const jsonBlob = new Blob([formatter.toJSON()], { type: 'application/json' });
    this.jsonBlobUrl = this.sanitizer.sanitize(SecurityContext.URL, URL.createObjectURL(jsonBlob))!;

    const xmlBlob = new Blob([formatter.toXML()], { type: 'application/xml' });
    this.xmlBlobUrl = this.sanitizer.sanitize(SecurityContext.URL, URL.createObjectURL(xmlBlob))!;
  }

  ngOnDestroy() {
    this.clearStepScopedSubscriptions();
    this.parameterSubscription?.cancel();
    this.commandSubscription?.cancel();
  }
}
