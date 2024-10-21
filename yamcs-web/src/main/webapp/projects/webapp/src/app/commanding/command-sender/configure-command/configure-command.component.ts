import { Location } from '@angular/common';
import { AfterViewInit, ChangeDetectionStrategy, Component, input, OnDestroy, OnInit, signal, ViewChild } from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatStepperIcon, MatStepperModule } from '@angular/material/stepper';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { Clearance, Command, CommandHistoryEntry, ConfigService, CreateTimelineItemRequest, MessageService, WebappSdkModule, WebsiteConfig, YamcsService, YaStepperStep } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { AuthService } from '../../../core/services/AuthService';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { SignificanceLevelComponent } from '../../../shared/significance-level/significance-level.component';
import { CommandFormComponent } from '../command-form/command-form.component';
import { TemplateProvider } from '../command-form/TemplateProvider';
import { ScheduleCommandDialogComponent } from '../schedule-command-dialog/schedule-command-dialog.component';
import { SendCommandWizardStepComponent } from '../send-command-wizard-step/send-command-wizard-step.component';
import { CommandConstraintsComponent } from './command-constraints.component';
import { CommandHistoryTemplateProvider } from './CommandHistoryTemplateProvider';

@Component({
  standalone: true,
  templateUrl: './configure-command.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommandConstraintsComponent,
    CommandFormComponent,
    InstancePageTemplateComponent,
    InstanceToolbarComponent,
    MarkdownComponent,
    MatIconModule,
    MatStepperIcon,
    MatStepperModule,
    SendCommandWizardStepComponent,
    SignificanceLevelComponent,
    WebappSdkModule,
    YaStepperStep,
  ],
})
export class ConfigureCommandComponent implements OnInit, AfterViewInit, OnDestroy {

  qualifiedName = input.required<string>({ alias: 'command' });

  @ViewChild('commandForm')
  commandForm: CommandFormComponent;

  config: WebsiteConfig;

  command = signal<Command | null>(null);
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
      this.command.set(command);
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
  }

  goBack() {
    this.location.back();
  }

  sendCommand() {
    this.armControl.setValue(false);

    const qname = this.qualifiedName();
    const commandConfig = this.commandForm.getResult();

    this.yamcs.yamcsClient.issueCommand(this.yamcs.instance!, this.yamcs.processor!, qname, {
      args: commandConfig.args,
      extra: commandConfig.extra,
      stream: commandConfig.stream,
      comment: commandConfig.comment,
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
        const commandConfig = this.commandForm.getResult(true);

        const options: CreateTimelineItemRequest = {
          type: 'ACTIVITY',
          duration: '0s',
          name: qname,
          start: scheduleOptions['executionTime'],
          tags: scheduleOptions['tags'],
          activityDefinition: {
            type: 'COMMAND',
            args: {
              processor: this.yamcs.processor!,
              command: qname,
              args: commandConfig.args,
              extra: commandConfig.extra,
              stream: commandConfig.stream,
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
