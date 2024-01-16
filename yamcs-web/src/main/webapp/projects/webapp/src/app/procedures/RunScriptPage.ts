import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormGroup, UntypedFormBuilder, Validators } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { ActivityDefinition, CreateTimelineItemRequest, MessageService, SelectOption, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../core/services/AuthService';
import { ScheduleScriptDialog } from './ScheduleScriptDialog';

@Component({
  templateUrl: './RunScriptPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RunScriptPage {

  form: FormGroup;

  scriptOptions$ = new BehaviorSubject<SelectOption[]>([]);

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
      .then(activity => this.router.navigateByUrl(`/activities/${activity.id}?c=${this.yamcs.context}`))
      .catch(err => this.messageService.showError(err));
  }

  showSchedule() {
    const capabilities = this.yamcs.connectionInfo$.value?.instance?.capabilities || [];
    return capabilities.indexOf('activities') !== -1
      && this.authService.getUser()!.hasSystemPrivilege('ControlActivities');
  }

  openScheduleScriptDialog() {
    this.dialog.open(ScheduleScriptDialog, {
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
        processor: this.yamcs.instance || null,
        script: formValue['script'],
      },
    };
    if (formValue['args']) {
      options.args!['args'] = formValue['args'];
    }
    return options;
  }
}
