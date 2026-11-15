import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import {
  AuthService,
  Trigger,
  TriggerFiringOutcome,
  YaSelectOption,
  YamcsService,
} from '@yamcs/webapp-sdk';

/**
 * Whether the TriggerService is configured for the current instance
 */
export function hasTriggerService(yamcs: YamcsService) {
  const capabilities = yamcs.connectionInfo$.value?.instance.capabilities ?? [];
  return capabilities.includes('activity-triggers');
}

export function mayReadTriggers(authService: AuthService) {
  const user = authService.getUser()!;
  return (
    user.hasSystemPrivilege('ReadActivityTriggers') ||
    user.hasSystemPrivilege('ControlActivityTriggers')
  );
}

/**
 * Enable or disable triggers, including the global kill switch
 */
export function mayToggleTriggers(authService: AuthService) {
  return authService.getUser()!.hasSystemPrivilege('ControlActivityTriggers');
}

/**
 * Create, update or delete triggers
 */
export function mayEditTriggers(authService: AuthService) {
  const user = authService.getUser()!;
  return (
    user.hasSystemPrivilege('ControlActivityTriggers') &&
    user.hasSystemPrivilege('ControlActivities')
  );
}

export const mayReadTriggersGuardFn: CanActivateFn = (route, state) => {
  if (!hasTriggerService(inject(YamcsService))) {
    return inject(Router).createUrlTree(['/activities'], {
      queryParams: route.queryParams,
    });
  }
  if (mayReadTriggers(inject(AuthService))) {
    return true;
  }
  return inject(Router).createUrlTree(['/403'], {
    queryParams: { page: state.url },
  });
};

export const outcomeOptions: YaSelectOption[] = [
  { id: 'STARTED', label: 'Started' },
  { id: 'PENDING_CONFIRMATION', label: 'Pending confirmation' },
  { id: 'SUPPRESSED_COOLDOWN', label: 'Suppressed (cooldown)' },
  { id: 'SUPPRESSED_CONCURRENCY', label: 'Suppressed (concurrency)' },
  { id: 'SUPPRESSED_RATE', label: 'Suppressed (rate limit)' },
  { id: 'REJECTED', label: 'Rejected' },
  { id: 'EXPIRED', label: 'Expired' },
  { id: 'FAILED_TO_START', label: 'Failed to start' },
];

export function outcomeLabel(outcome: TriggerFiringOutcome) {
  return outcomeOptions.find((o) => o.id === outcome)?.label ?? outcome;
}

/**
 * One-line summary of the condition of a trigger
 */
export function conditionSummary(trigger: Trigger) {
  const alarm = trigger.alarmCondition;
  if (alarm) {
    const what =
      alarm.alarmType === 'EVENT' ? 'Event alarm' : 'Parameter alarm';
    const subjects = alarm.subjects?.length ? alarm.subjects.join(', ') : 'any';
    const severity = alarm.minSeverity ?? 'WATCH';
    return `${what} on ${subjects} (≥ ${severity})`;
  }
  const param = trigger.parameterCondition;
  if (param) {
    const edge = param.edge ?? 'RISING';
    const suffix = edge === 'RISING' ? '' : ` (${edge.toLowerCase()})`;
    return param.expression + suffix;
  }
  return '-';
}

/**
 * One-line summary of the activity of a trigger
 */
export function activitySummary(trigger: Trigger) {
  const activity = trigger.activity;
  if (!activity) {
    return '-';
  }
  const args = activity.args || {};
  let detail: string | undefined;
  switch (activity.type) {
    case 'COMMAND':
      detail = args['command'];
      break;
    case 'SCRIPT':
      detail = args['script'];
      break;
    case 'STACK':
      detail = args['stack'];
      break;
    case 'MANUAL':
      detail = args['name'];
      break;
  }
  if (!detail && activity.description) {
    detail = activity.description;
  }
  return detail ? `${activity.type}: ${detail}` : activity.type;
}

/**
 * Protobuf int64 fields may be encoded as JSON strings
 */
export function toNumber(value: number | string | undefined | null) {
  if (value === undefined || value === null || value === '') {
    return undefined;
  }
  return Number(value);
}
