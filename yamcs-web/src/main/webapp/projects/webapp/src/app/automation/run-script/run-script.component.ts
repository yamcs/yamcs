import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, Validators } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import {
  ActivityDefinition,
  AuthService,
  MessageService,
  SaveTimelineItemRequest,
  WebappSdkModule,
  YaHelpDialog,
  YaSelectOption,
  YamcsService,
  utils,
} from '@yamcs/webapp-sdk';
import {
  ScheduleActivityDialogComponent,
  ScheduleActivityDialogData,
  ScheduleActivityDialogResult,
} from '../../shared/schedule-activity-dialog/schedule-activity-dialog.component';

@Component({
  templateUrl: './run-script.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class RunScriptComponent {
  private fb = inject(NonNullableFormBuilder);

  form = this.fb.group({
    runner: ['', Validators.required],
    script: ['', Validators.required],
    args: '',
  });

  // Signal following runner form control
  runnerValue = toSignal(this.form.controls.runner.valueChanges, {
    initialValue: '',
  });

  runnerOptions = signal<YaSelectOption[]>([]);
  scripts = signal<string[]>([]);

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private authService: AuthService,
    private router: Router,
    private dialog: MatDialog,
  ) {
    title.setTitle('Run a script');

    yamcs.yamcsClient
      .getScriptRunners(this.yamcs.instance!)
      .then((page) => {
        const runnerOptions = (page.runners || []).map((runner) => {
          return { id: runner.name, label: runner.name };
        });
        this.runnerOptions.set(runnerOptions);

        // Select first runner by default
        if (runnerOptions.length) {
          this.form.controls.runner.setValue(runnerOptions[0].id);
        }
      })
      .catch((err) => this.messageService.showError(err));

    effect(() => {
      const selectedRunner = this.runnerValue();
      if (selectedRunner) {
        // Reset script selection
        this.form.controls.script.setValue('');
        this.form.controls.args.setValue('');

        yamcs.yamcsClient
          .getActivityScripts(this.yamcs.instance!, selectedRunner)
          .then((page) => {
            const scripts = page.scripts || [];
            this.scripts.set(scripts);

            // Select first script by default
            if (scripts.length > 0) {
              this.form.controls.script.setValue(scripts[0]);
            }
          })
          .catch((err) => messageService.showError(err));
      }
    });
  }

  runScript() {
    const options = this.createActivityDefinition();
    this.yamcs.yamcsClient
      .startActivity(this.yamcs.instance!, options)
      .then((activity) => {
        if (this.authService.getUser()!.hasSystemPrivilege('ReadActivities')) {
          this.router.navigateByUrl(
            `/activities/${activity.id}?c=${this.yamcs.context}`,
          );
        } else {
          this.dialog
            .open(YaHelpDialog, {
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
            })
            .afterClosed()
            .subscribe(() => this.form.reset());
        }
      })
      .catch((err) => this.messageService.showError(err));
  }

  showSchedule() {
    const capabilities =
      this.yamcs.connectionInfo$.value?.instance?.capabilities || [];
    return (
      capabilities.indexOf('timeline') !== -1 &&
      capabilities.indexOf('activities') !== -1 &&
      this.authService.getUser()!.hasSystemPrivilege('ControlTimeline')
    );
  }

  openScheduleScriptDialog() {
    this.dialog
      .open<
        ScheduleActivityDialogComponent,
        ScheduleActivityDialogData,
        ScheduleActivityDialogResult
      >(ScheduleActivityDialogComponent, {
        width: '600px',
        data: {
          type: 'script',
          name: `Run ${utils.getFilename(this.form.value.script!)}`,
        },
      })
      .afterClosed()
      .subscribe((scheduleOptions) => {
        if (scheduleOptions) {
          const options: SaveTimelineItemRequest = {
            type: 'ACTIVITY',
            ...scheduleOptions,
            activityDefinition: this.createActivityDefinition(),
          };

          this.yamcs.yamcsClient
            .createTimelineItem(this.yamcs.instance!, options)
            .then(() => {
              this.messageService.showInfo('Script scheduled');
              this.router.navigateByUrl(`/activities?c=${this.yamcs.context}`);
            })
            .catch((err) => this.messageService.showError(err));
        }
      });
  }

  private createActivityDefinition(): ActivityDefinition {
    const formValue = this.form.value;
    const options: ActivityDefinition = {
      type: 'SCRIPT',
      args: {
        runner: formValue.runner,
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
