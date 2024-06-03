import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormGroup, UntypedFormBuilder, Validators } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { ActivityDefinition, CreateTimelineItemRequest, MessageService, WebappSdkModule, YaHelpDialog, YaSelectOption, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';
import { ScheduleScriptDialogComponent } from '../schedule-script-dialog/schedule-script-dialog.component';

@Component({
  standalone: true,
  templateUrl: './run-script.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class RunScriptComponent {

  form: FormGroup;

  scriptOptions$ = new BehaviorSubject<YaSelectOption[]>([]);

  constructor(
    title: Title,
    formBuilder: UntypedFormBuilder,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private authService: AuthService,
    private router: Router,
    private dialog: MatDialog,
  ) {
    title.setTitle('Run a script');
    this.form = formBuilder.group({
      script: ['', [Validators.required]],
      args: [''],
    });

    yamcs.yamcsClient.getActivityScripts(this.yamcs.instance!)
      .then(page => {
        for (const script of page.scripts || []) {
          this.scriptOptions$.next([
            ...this.scriptOptions$.value,
            { id: script, label: script },
          ]);
        }
      })
      .catch(err => messageService.showError(err));
  }

  runScript() {
    const options = this.createActivityDefinition();
    this.yamcs.yamcsClient.startActivity(this.yamcs.instance!, options)
      .then(activity => {
        if (this.authService.getUser()!.hasSystemPrivilege('ReadActivities')) {
          this.router.navigateByUrl(`/activities/${activity.id}?c=${this.yamcs.context}`);
        } else {
          this.dialog.open(YaHelpDialog, {
            width: '500px',
            data: {
              icon: 'done',
              closeText: 'OK',
              content: `
                <p>The procedure has started executing.</p>
                <p>
                  Note that you do not have sufficient privileges
                  to follow up on submitted procedures.
                </p>
              `,
            },
          }).afterClosed().subscribe(() => this.form.reset());
        }
      })
      .catch(err => this.messageService.showError(err));
  }

  showSchedule() {
    const capabilities = this.yamcs.connectionInfo$.value?.instance?.capabilities || [];
    return capabilities.indexOf('timeline') !== -1
      && capabilities.indexOf('activities') !== -1
      && this.authService.getUser()!.hasSystemPrivilege('ControlTimeline');
  }

  openScheduleScriptDialog() {
    this.dialog.open(ScheduleScriptDialogComponent, {
      width: '600px',
    }).afterClosed().subscribe(scheduleOptions => {
      const formValue = this.form.value;

      if (scheduleOptions) {
        const options: CreateTimelineItemRequest = {
          type: 'ACTIVITY',
          duration: '0s',
          name: formValue['script'],
          start: scheduleOptions['executionTime'],
          tags: scheduleOptions['tags'],
          activityDefinition: this.createActivityDefinition(),
        };

        this.yamcs.yamcsClient.createTimelineItem(this.yamcs.instance!, options)
          .then(() => {
            this.messageService.showInfo('Script scheduled');
            this.router.navigateByUrl(`/activities?c=${this.yamcs.context}`);
          })
          .catch(err => this.messageService.showError(err));
      }
    });
  }

  private createActivityDefinition(): ActivityDefinition {
    const formValue = this.form.value;
    const options: ActivityDefinition = {
      type: 'SCRIPT',
      args: {
        processor: this.yamcs.processor || null,
        script: formValue['script'],
      },
    };
    if (formValue['args']) {
      options.args!['args'] = formValue['args'];
    }
    return options;
  }
}
