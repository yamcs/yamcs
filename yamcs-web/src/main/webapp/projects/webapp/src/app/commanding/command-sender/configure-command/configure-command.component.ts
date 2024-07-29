import { Location } from '@angular/common';
import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, ViewChild, input } from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { Clearance, Command, CommandHistoryEntry, CommandOptionType, ConfigService, CreateTimelineItemRequest, MessageService, Value, WebappSdkModule, WebsiteConfig, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { AuthService } from '../../../core/services/AuthService';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { LiveExpressionComponent } from '../../../shared/live-expression/live-expression.component';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { SignificanceLevelComponent } from '../../../shared/significance-level/significance-level.component';
import { CommandFormComponent, TemplateProvider } from '../command-form/command-form.component';
import { ScheduleCommandDialogComponent } from '../schedule-command-dialog/schedule-command-dialog.component';
import { SendCommandWizardStepComponent } from '../send-command-wizard-step/send-command-wizard-step.component';

@Component({
  standalone: true,
  templateUrl: './configure-command.component.html',
  styleUrl: './configure-command.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommandFormComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    LiveExpressionComponent,
    MarkdownComponent,
    SendCommandWizardStepComponent,
    WebappSdkModule,
    SignificanceLevelComponent,
  ],
})
export class ConfigureCommandComponent implements OnInit, AfterViewInit, OnDestroy {

  qualifiedName = input.required<string>({ alias: 'command' });

  @ViewChild('commandForm')
  commandForm: CommandFormComponent;

  config: WebsiteConfig;

  command$ = new BehaviorSubject<Command | null>(null);
  templateProvider$ = new BehaviorSubject<TemplateProvider | null>(null);
  cleared$ = new BehaviorSubject<boolean>(true);

  private connectionInfoSubscription: Subscription;

  armControl = new UntypedFormControl();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private title: Title,
    private messageService: MessageService,
    readonly yamcs: YamcsService,
    private location: Location,
    configService: ConfigService,
    private authService: AuthService,
    private changeDetection: ChangeDetectorRef,
    private dialog: MatDialog,
  ) {
    this.config = configService.getConfig();
  }

  ngOnInit(): void {
    this.title.setTitle(`Send a command: ${this.qualifiedName()}`);

    const promises: Promise<any>[] = [
      this.yamcs.yamcsClient.getCommand(this.yamcs.instance!, this.qualifiedName()),
    ];

    const templateId = this.route.snapshot.queryParamMap.get('template');
    if (templateId) {
      const promise = this.yamcs.yamcsClient.getCommandHistoryEntry(this.yamcs.instance!, templateId);
      promises.push(promise);
    }

    Promise.all(promises).then(responses => {
      const command = responses[0] as Command;
      let template: CommandHistoryEntry | undefined;
      if (responses.length > 1) {
        template = responses[1];
      }
      this.command$.next(command);
      if (template) {
        this.templateProvider$.next(new CommandHistoryTemplateProvider(template));
      } else {
        this.templateProvider$.next(null);
      }

      if (this.config.commandClearanceEnabled) {
        this.connectionInfoSubscription = this.yamcs.clearance$.subscribe(clearance => {
          const significance = command.effectiveSignificance;
          this.cleared$.next(this.isCleared(clearance, significance?.consequenceLevel));
        });
      }
    });
  }

  ngAfterViewInit() {
    if (this.config.twoStageCommanding) {
      this.commandForm.form.valueChanges.subscribe(() => {
        this.armControl.setValue(false);
      });
      this.commandForm.form.statusChanges.subscribe(() => {
        if (this.commandForm.form.valid) {
          this.armControl.enable();
        } else {
          this.armControl.disable();
        }
      });
    }

    this.commandForm.hasArguments$.subscribe(hasArguments => {
      this.changeDetection.detectChanges();
    });
  }

  goBack() {
    this.location.back();
  }

  sendCommand() {
    this.armControl.setValue(false);

    const args = this.commandForm.getAssignments();
    const comment = this.commandForm.getComment();
    const extra = this.commandForm.getExtraOptions();

    const qname = this.qualifiedName();

    this.yamcs.yamcsClient.issueCommand(this.yamcs.instance!, this.yamcs.processor!, qname, {
      args,
      comment,
      extra,
    }).then(response => {
      this.router.navigate([
        '/commanding/send' + qname,
        '-',
        'report',
        response.id,
      ], {
        queryParams: {
          c: this.yamcs.context,
        }
      });
    }).catch(err => {
      this.messageService.showError(err);
    });
  }

  showSchedule() {
    const capabilities = this.yamcs.connectionInfo$.value?.instance?.capabilities || [];
    return capabilities.indexOf('timeline') !== -1
      && capabilities.indexOf('activities') !== -1
      && this.authService.getUser()!.hasSystemPrivilege('ControlTimeline');
  }

  openScheduleCommandDialog() {
    this.dialog.open(ScheduleCommandDialogComponent, {
      width: '600px',
    }).afterClosed().subscribe(scheduleOptions => {
      if (scheduleOptions) {
        this.armControl.setValue(false);

        const qname = this.qualifiedName();
        const args = this.commandForm.getAssignments();
        const extra = this.commandForm.getExtraOptions(true);

        const options: CreateTimelineItemRequest = {
          type: 'ACTIVITY',
          duration: '0s',
          name: qname,
          start: scheduleOptions['executionTime'],
          tags: scheduleOptions['tags'],
          activityDefinition: {
            "type": "COMMAND",
            "args": {
              "processor": this.yamcs.processor!,
              "command": qname,
              "args": args,
              "extra": extra,
            }
          },
        };

        this.yamcs.yamcsClient.createTimelineItem(this.yamcs.instance!, options)
          .then(() => {
            this.messageService.showInfo('Command scheduled');
            this.router.navigateByUrl(`/commanding/send?c=${this.yamcs.context}`);
          })
          .catch(err => this.messageService.showError(err));
      }
    });
  }

  private isCleared(clearance: Clearance | null, level?: string) {
    if (!clearance) {
      return false;
    }

    switch (clearance.level) {
      case 'SEVERE':
        if (level === 'SEVERE') {
          return true;
        }
      // fall
      case 'CRITICAL':
        if (level === 'CRITICAL') {
          return true;
        }
      // fall
      case 'DISTRESS':
        if (level === 'DISTRESS') {
          return true;
        }
      // fall
      case 'WARNING':
        if (level === 'WARNING') {
          return true;
        }
      // fall
      case 'WATCH':
        if (level === 'WATCH') {
          return true;
        }
      // fall
      case 'NONE':
        if (level === 'NONE' || !level) {
          return true;
        }
    }

    return false;
  }

  ngOnDestroy() {
    this.connectionInfoSubscription?.unsubscribe();
  }
}

export class CommandHistoryTemplateProvider implements TemplateProvider {

  constructor(private entry: CommandHistoryEntry) {
  }

  getAssignment(argumentName: string) {
    if (this.entry.assignments) {
      for (const assignment of this.entry.assignments) {
        if (assignment.name === argumentName) {
          return assignment.value;
        }
      }
    }
  }

  getOption(id: string, expectedType: CommandOptionType) {
    for (const attr of (this.entry.attr || [])) {
      if (attr.name === id) {
        switch (expectedType) {
          case 'BOOLEAN':
            return this.getBooleanOption(attr.value);
          case 'NUMBER':
            return this.getNumberOption(attr.value);
          case 'STRING':
            return this.getStringOption(attr.value);
          case 'TIMESTAMP':
            return this.getTimestampOption(attr.value);
        }
      }
    }
  }

  private getBooleanOption(value: Value) {
    if (value.type === 'BOOLEAN') {
      return value;
    }
  }

  private getNumberOption(value: Value) {
    switch (value.type) {
      case 'SINT32':
      case 'UINT32':
      case 'SINT64':
      case 'UINT64':
        return value;
    }
  }

  private getStringOption(value: Value) {
    if (value.type === 'STRING') {
      return value;
    }
  }

  private getTimestampOption(value: Value) {
    if (value.type === 'TIMESTAMP') {
      return value;
    }
  }

  getComment() {
    // Don't copy
  }
}
