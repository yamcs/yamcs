import { Location } from '@angular/common';
import {
  Component,
  computed,
  inject,
  input,
  OnInit,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, Validators } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import {
  AuthService,
  ConfigService,
  MessageService,
  SaveTriggerRequest,
  TestTriggerResponse,
  Trigger,
  WebappSdkModule,
  YaSelectOption,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { SelectParameterDialogComponent } from '../../../shared/select-parameter-dialog/select-parameter-dialog.component';
import {
  mayEditTriggers,
  mayToggleTriggers,
  toNumber,
} from '../shared/triggers';

const BUILTIN_TYPES = ['COMMAND', 'SCRIPT', 'STACK', 'MANUAL'];

@Component({
  templateUrl: './trigger-edit.component.html',
  styleUrl: './trigger-edit.component.css',
  imports: [WebappSdkModule],
})
export class TriggerEditComponent implements OnInit {
  readonly yamcs = inject(YamcsService);
  readonly location = inject(Location);
  private authService = inject(AuthService);
  private configService = inject(ConfigService);
  private messageService = inject(MessageService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private dialog = inject(MatDialog);
  private title = inject(Title);
  private fb = inject(NonNullableFormBuilder);

  /**
   * Trigger id or name, absent when creating a new trigger
   */
  trigger = input<string>();

  existing = signal<Trigger | null>(null);
  readOnly = signal(false);
  saveError = signal<string | null>(null);
  testResult = signal<TestTriggerResponse | null>(null);
  subjects = signal<string[]>([]);

  conditionModeOptions: YaSelectOption[] = [
    { id: 'ALARM', label: 'Alarm notification' },
    { id: 'PARAMETER', label: 'Parameter condition' },
  ];
  alarmTypeOptions: YaSelectOption[] = [
    { id: 'PARAMETER', label: 'Parameter alarm' },
    { id: 'EVENT', label: 'Event alarm' },
  ];
  notificationOptions: YaSelectOption[] = [
    'TRIGGERED',
    'SEVERITY_INCREASED',
    'VALUE_UPDATED',
    'ACKNOWLEDGED',
    'RTN',
    'CLEARED',
    'RESET',
    'SHELVED',
    'UNSHELVED',
  ].map((id) => ({ id, label: id }));
  severityOptions: YaSelectOption[] = [
    'WATCH',
    'WARNING',
    'DISTRESS',
    'CRITICAL',
    'SEVERE',
  ].map((id) => ({ id, label: id }));
  edgeOptions: YaSelectOption[] = [
    { id: 'RISING', label: 'Rising (becomes true)' },
    { id: 'FALLING', label: 'Falling (becomes false)' },
    { id: 'BOTH', label: 'Both' },
  ];
  concurrencyOptions: YaSelectOption[] = [
    { id: 'SKIP', label: 'Skip (do not start a new activity)' },
    { id: 'ALLOW', label: 'Allow (start a new activity anyway)' },
    { id: 'CANCEL_PREVIOUS', label: 'Cancel previous, then start' },
  ];
  activityTypeOptions = signal<YaSelectOption[]>([
    { id: 'COMMAND', label: 'Command' },
    { id: 'SCRIPT', label: 'Script' },
    { id: 'STACK', label: 'Stack' },
    { id: 'MANUAL', label: 'Manual' },
  ]);
  runnerOptions = signal<YaSelectOption[]>([]);
  scripts = signal<string[]>([]);

  form = this.fb.group({
    name: ['', Validators.required],
    description: '',
    enabled: true,
    processor: '',
    runAs: '',
    conditionMode: 'ALARM',
    alarm: this.fb.group({
      alarmType: 'PARAMETER',
      notifications: [['TRIGGERED', 'SEVERITY_INCREASED']],
      minSeverity: 'WATCH',
      includeShelved: false,
    }),
    parameter: this.fb.group({
      expression: '',
      edge: 'RISING',
      holdTime: 0,
      invalidAsFalse: true,
    }),
    policy: this.fb.group({
      cooldown: 60000,
      concurrency: 'SKIP',
      requireConfirmation: false,
      confirmationTimeout: 300000,
      maxFiringsPerHour: 0,
    }),
    activity: this.fb.group({
      type: ['COMMAND', Validators.required],
      label: '',
      // COMMAND
      command: '',
      commandArgs: '',
      // SCRIPT
      runner: '',
      script: '',
      scriptArgs: '',
      // STACK
      bucket: '',
      stack: '',
      // MANUAL
      manualName: '',
      // Any other type, and arguments not covered by the fields above
      json: '',
    }),
  });
  subjectInput = this.fb.control('');
  testContext = this.fb.control('');

  private formValue = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });
  conditionMode = computed(() => this.formValue().conditionMode);
  alarmType = computed(() => this.formValue().alarm?.alarmType);
  activityType = computed(() => this.formValue().activity?.type ?? '');
  isBuiltinType = computed(() => BUILTIN_TYPES.includes(this.activityType()));
  policyValue = computed(() => this.formValue().policy);

  ngOnInit() {
    const { yamcs } = this;
    this.form.controls.activity.controls.bucket.setValue(
      this.configService.getStackBucket(),
    );

    yamcs.yamcsClient
      .getExecutors(yamcs.instance!)
      .then((executors) => {
        const options = [...this.activityTypeOptions()];
        for (const executor of executors) {
          if (!options.some((o) => o.id === executor.type)) {
            options.push({ id: executor.type, label: executor.displayName });
          }
        }
        this.activityTypeOptions.set(options);
      })
      .catch((err) => this.messageService.showError(err));

    yamcs.yamcsClient
      .getScriptRunners(yamcs.instance!)
      .then((page) => {
        const runners = (page.runners || []).map((r) => ({
          id: r.name,
          label: r.name,
        }));
        this.runnerOptions.set(runners);
        const runner = this.form.controls.activity.controls.runner;
        if (!runner.value && runners.length) {
          runner.setValue(runners[0].id);
        }
        this.loadScripts();
      })
      .catch((err) => this.messageService.showError(err));

    this.form.controls.conditionMode.valueChanges.subscribe(() =>
      this.resetTestContext(),
    );
    this.form.controls.alarm.controls.alarmType.valueChanges.subscribe(() =>
      this.resetTestContext(),
    );

    const triggerId = this.trigger();
    if (triggerId) {
      yamcs.yamcsClient
        .getTrigger(yamcs.instance!, triggerId)
        .then((trigger) => this.setTrigger(trigger))
        .catch((err) => this.messageService.showError(err));
    } else {
      this.title.setTitle('Create trigger');
      if (!mayEditTriggers(this.authService)) {
        this.readOnly.set(true);
        this.form.disable();
      }
      this.resetTestContext();
    }
  }

  mayToggle() {
    return mayToggleTriggers(this.authService);
  }

  loadScripts() {
    const runner = this.form.controls.activity.controls.runner.value;
    if (!runner) {
      this.scripts.set([]);
      return;
    }
    this.yamcs.yamcsClient
      .getActivityScripts(this.yamcs.instance!, runner)
      .then((page) => this.scripts.set(page.scripts || []))
      .catch((err) => this.messageService.showError(err));
  }

  addSubject() {
    const value = this.subjectInput.value.trim();
    if (value) {
      this.subjects.set([...this.subjects(), value]);
      this.subjectInput.setValue('');
    }
  }

  browseSubjectParameter() {
    this.dialog
      .open(SelectParameterDialogComponent, {
        width: '500px',
        data: {
          label: 'Add parameter or pattern',
          okLabel: 'ADD',
          hint: 'Either an exact parameter name, or a glob pattern with * or ? wildcards.',
          exclude: this.subjects(),
        },
      })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.subjects.set([...this.subjects(), result]);
        }
      });
  }

  removeSubject(idx: number) {
    const subjects = [...this.subjects()];
    subjects.splice(idx, 1);
    this.subjects.set(subjects);
  }

  insertExpressionParameter() {
    this.dialog
      .open(SelectParameterDialogComponent, {
        width: '500px',
        data: { okLabel: 'INSERT' },
      })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          const control = this.form.controls.parameter.controls.expression;
          const current = control.value.trimEnd();
          control.setValue(current ? `${current} ${result}` : result);
          this.resetTestContext();
        }
      });
  }

  save() {
    this.saveError.set(null);
    let request: SaveTriggerRequest;
    try {
      request = this.createRequest();
    } catch (err: any) {
      this.saveError.set(err.message);
      return;
    }

    const { yamcs } = this;
    const existing = this.existing();
    const promise = existing
      ? yamcs.yamcsClient.updateTrigger(yamcs.instance!, existing.id, request)
      : yamcs.yamcsClient.createTrigger(yamcs.instance!, request);
    promise
      .then((trigger) => {
        if (existing) {
          this.setTrigger(trigger);
          this.messageService.showInfo('Trigger saved');
        } else {
          this.router.navigate(['..', trigger.id], {
            relativeTo: this.route,
            queryParams: { c: yamcs.context },
            replaceUrl: true,
          });
        }
      })
      .catch((err) => this.saveError.set(err.message || String(err)));
  }

  test() {
    const existing = this.existing();
    if (!existing) {
      return;
    }
    let context: { [key: string]: any } | undefined;
    try {
      const text = this.testContext.value.trim();
      context = text ? JSON.parse(text) : undefined;
    } catch (err: any) {
      this.testResult.set({ error: `Invalid sample context: ${err.message}` });
      return;
    }
    this.yamcs.yamcsClient
      .testTrigger(this.yamcs.instance!, existing.id, { context })
      .then((result) => this.testResult.set(result))
      .catch((err) => this.messageService.showError(err));
  }

  setEnabled(enabled: boolean) {
    const existing = this.existing();
    if (!existing) {
      return;
    }
    const { yamcs } = this;
    const promise = enabled
      ? yamcs.yamcsClient.enableTrigger(yamcs.instance!, existing.id)
      : yamcs.yamcsClient.disableTrigger(yamcs.instance!, existing.id);
    promise
      .then((trigger) => {
        this.existing.set(trigger);
        this.form.controls.enabled.setValue(trigger.enabled);
      })
      .catch((err) => this.messageService.showError(err));
  }

  deleteTrigger() {
    const existing = this.existing();
    if (!existing) {
      return;
    }
    if (
      !confirm(`Are you sure you want to delete trigger '${existing.name}'?`)
    ) {
      return;
    }
    this.yamcs.yamcsClient
      .deleteTrigger(this.yamcs.instance!, existing.id)
      .then(() =>
        this.router.navigate(['..'], {
          relativeTo: this.route,
          queryParams: { c: this.yamcs.context },
        }),
      )
      .catch((err) => this.messageService.showError(err));
  }

  private setTrigger(trigger: Trigger) {
    this.existing.set(trigger);
    this.title.setTitle(trigger.name);

    const readOnly =
      trigger.source === 'CONFIG' || !mayEditTriggers(this.authService);
    this.readOnly.set(readOnly);

    const alarm = trigger.alarmCondition;
    const param = trigger.parameterCondition;
    const policy = trigger.policy || {};
    const activity = trigger.activity || { type: 'MANUAL' };
    const args = { ...(activity.args || {}) };

    const activityValue: { [key: string]: any } = {
      type: activity.type,
      label: activity.label ?? '',
    };
    // Move known arguments to their own fields, keep the rest as JSON
    switch (activity.type) {
      case 'COMMAND':
        activityValue['command'] = takeString(args, 'command');
        activityValue['commandArgs'] =
          args['args'] !== undefined
            ? JSON.stringify(args['args'], null, 2)
            : '';
        delete args['args'];
        break;
      case 'SCRIPT': {
        activityValue['runner'] = takeString(args, 'runner');
        activityValue['script'] = takeString(args, 'script');
        const scriptArgs = args['args'];
        activityValue['scriptArgs'] = Array.isArray(scriptArgs)
          ? scriptArgs.join(' ')
          : (scriptArgs ?? '');
        delete args['args'];
        break;
      }
      case 'STACK':
        activityValue['bucket'] = takeString(args, 'bucket');
        activityValue['stack'] = takeString(args, 'stack');
        break;
      case 'MANUAL':
        activityValue['manualName'] = takeString(args, 'name');
        break;
    }
    activityValue['json'] = Object.keys(args).length
      ? JSON.stringify(args, null, 2)
      : '';

    this.subjects.set(alarm?.subjects ?? []);
    this.form.enable();
    this.form.patchValue({
      name: trigger.name,
      description: trigger.description ?? '',
      enabled: trigger.enabled,
      processor: trigger.processor ?? '',
      runAs: trigger.runAs ?? '',
      conditionMode: param ? 'PARAMETER' : 'ALARM',
      alarm: {
        alarmType: alarm?.alarmType ?? 'PARAMETER',
        notifications: alarm?.notifications ?? [
          'TRIGGERED',
          'SEVERITY_INCREASED',
        ],
        minSeverity: alarm?.minSeverity ?? 'WATCH',
        includeShelved: alarm?.includeShelved ?? false,
      },
      parameter: {
        expression: param?.expression ?? '',
        edge: param?.edge ?? 'RISING',
        holdTime: toNumber(param?.holdTime) ?? 0,
        invalidAsFalse: param?.invalidAsFalse ?? true,
      },
      policy: {
        cooldown: toNumber(policy.cooldown) ?? 60000,
        concurrency: policy.concurrency ?? 'SKIP',
        requireConfirmation: policy.requireConfirmation ?? false,
        confirmationTimeout: toNumber(policy.confirmationTimeout) ?? 300000,
        maxFiringsPerHour: policy.maxFiringsPerHour ?? 0,
      },
      activity: activityValue,
    });
    if (readOnly) {
      this.form.disable();
    }
    this.loadScripts();
    this.resetTestContext();
  }

  private createRequest(): SaveTriggerRequest {
    const value = this.form.getRawValue();
    const request: SaveTriggerRequest = {
      name: value.name,
      enabled: value.enabled,
      policy: {
        cooldown: Number(value.policy.cooldown),
        concurrency: value.policy.concurrency as any,
        requireConfirmation: value.policy.requireConfirmation,
        confirmationTimeout: Number(value.policy.confirmationTimeout),
        maxFiringsPerHour: Number(value.policy.maxFiringsPerHour),
      },
      activity: this.createActivity(),
    };
    if (value.description) {
      request.description = value.description;
    }
    if (value.processor) {
      request.processor = value.processor;
    }
    if (value.runAs) {
      request.runAs = value.runAs;
    }
    if (value.conditionMode === 'ALARM') {
      request.alarmCondition = {
        alarmType: value.alarm.alarmType as any,
        subjects: this.subjects(),
        notifications: value.alarm.notifications as any,
        minSeverity: value.alarm.minSeverity as any,
        includeShelved: value.alarm.includeShelved,
      };
    } else {
      request.parameterCondition = {
        expression: value.parameter.expression,
        edge: value.parameter.edge as any,
        holdTime: Number(value.parameter.holdTime),
        invalidAsFalse: value.parameter.invalidAsFalse,
      };
    }
    return request;
  }

  private createActivity() {
    const value = this.form.controls.activity.getRawValue();
    const json = value.json.trim();
    let args: { [key: string]: any } = {};
    if (json) {
      try {
        args = JSON.parse(json);
      } catch (err: any) {
        throw new Error(`Invalid activity arguments: ${err.message}`);
      }
    }
    switch (value.type) {
      case 'COMMAND':
        args['command'] = value.command;
        if (value.commandArgs.trim()) {
          try {
            args['args'] = JSON.parse(value.commandArgs);
          } catch (err: any) {
            throw new Error(`Invalid command arguments: ${err.message}`);
          }
        }
        break;
      case 'SCRIPT':
        if (value.runner) {
          args['runner'] = value.runner;
        }
        args['script'] = value.script;
        if (value.scriptArgs.trim()) {
          args['args'] = value.scriptArgs;
        }
        break;
      case 'STACK':
        args['bucket'] = value.bucket;
        args['stack'] = value.stack;
        break;
      case 'MANUAL':
        args['name'] = value.manualName;
        break;
    }
    const activity: SaveTriggerRequest['activity'] = { type: value.type, args };
    if (value.label) {
      activity.label = value.label;
    }
    return activity;
  }

  /**
   * Fill the sample context with values that match the condition
   */
  private resetTestContext() {
    const value = this.form.getRawValue();
    let context: { [key: string]: any };
    if (value.conditionMode === 'ALARM') {
      const subject = this.subjects()[0] ?? '/example/parameter';
      context = {
        'alarm.subject': subject.replace(/[*?]/g, ''),
        'alarm.severity': 'CRITICAL',
        'alarm.notification': 'TRIGGERED',
        'alarm.type': value.alarm.alarmType,
        'alarm.seqNum': 1,
        value: 0,
      };
      if (value.alarm.alarmType === 'EVENT') {
        context['event.source'] = 'source';
        context['event.type'] = 'type';
        context['event.message'] = 'message';
        context['event.severity'] = 'ERROR';
        context['value'] = 'message';
      }
    } else {
      context = {
        'condition.expression': value.parameter.expression,
        'condition.edge': value.parameter.edge,
        value: 0,
      };
    }
    this.testContext.setValue(JSON.stringify(context, null, 2));
  }
}

function takeString(args: { [key: string]: any }, key: string): string {
  const value = args[key];
  delete args[key];
  return value === undefined || value === null ? '' : String(value);
}
