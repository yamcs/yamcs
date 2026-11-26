import { Component, inject, OnDestroy, signal } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import {
  AuthService,
  GetTriggerFiringsOptions,
  MessageService,
  TriggerFiring,
  TriggerFiringOutcome,
  TriggerFiringSubscription,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { ActivitiesPageTabsComponent } from '../shared/activities-page-tabs.component';
import { outcomeLabel, outcomeOptions } from '../shared/triggers';

const PENDING: TriggerFiringOutcome = 'PENDING_CONFIRMATION';

@Component({
  templateUrl: './firing-list.component.html',
  styleUrl: './firing-list.component.css',
  imports: [ActivitiesPageTabsComponent, WebappSdkModule],
})
export class FiringListComponent implements OnDestroy {
  readonly yamcs = inject(YamcsService);
  private authService = inject(AuthService);
  private messageService = inject(MessageService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  outcomeOptions = outcomeOptions;
  outcomeLabel = outcomeLabel;

  filterForm = new FormGroup({
    outcome: new FormControl<string[]>([], { nonNullable: true }),
    trigger: new FormControl<string>('', { nonNullable: true }),
  });

  pending = signal<TriggerFiring[]>([]);
  firings = signal<TriggerFiring[]>([]);

  displayedColumns = [
    'time',
    'trigger',
    'outcome',
    'activity',
    'decidedBy',
    'message',
  ];
  pendingColumns = ['time', 'trigger', 'context', 'actions'];

  private firingSubscription?: TriggerFiringSubscription;

  constructor() {
    inject(Title).setTitle('Trigger firings');

    const queryParams = this.route.snapshot.queryParamMap;
    const outcome = queryParams.getAll('outcome');
    this.filterForm.setValue({
      outcome,
      trigger: queryParams.get('trigger') ?? '',
    });
    this.filterForm.valueChanges.subscribe(() => {
      this.updateURL();
      this.loadFirings();
    });

    this.loadPending();
    this.loadFirings();

    this.firingSubscription =
      this.yamcs.yamcsClient.createTriggerFiringSubscription(
        { instance: this.yamcs.instance! },
        (firing) => this.processFiring(firing),
      );
  }

  mayDecide() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlActivities');
  }

  clearTrigger() {
    this.filterForm.controls.trigger.setValue('');
  }

  confirm(firing: TriggerFiring) {
    this.yamcs.yamcsClient
      .confirmTriggerFiring(this.yamcs.instance!, firing.id)
      .then((updated) => this.processFiring(updated))
      .catch((err) => this.messageService.showError(err));
  }

  reject(firing: TriggerFiring) {
    this.yamcs.yamcsClient
      .rejectTriggerFiring(this.yamcs.instance!, firing.id)
      .then((updated) => this.processFiring(updated))
      .catch((err) => this.messageService.showError(err));
  }

  contextSummary(firing: TriggerFiring) {
    const context = firing.context || {};
    const keys = ['alarm.subject', 'alarm.severity', 'event.message', 'value'];
    return keys
      .filter((k) => context[k] !== undefined)
      .map((k) => `${k}: ${context[k]}`)
      .join(', ');
  }

  private loadPending() {
    this.yamcs.yamcsClient
      .getTriggerFirings(this.yamcs.instance!, { outcome: PENDING })
      .then((firings) => this.pending.set(firings))
      .catch((err) => this.handleError(err));
  }

  private loadFirings() {
    const { outcome, trigger } = this.filterForm.getRawValue();
    const options: GetTriggerFiringsOptions = { limit: 200 };
    if (outcome.length) {
      options.outcome = outcome as TriggerFiringOutcome[];
    }
    if (trigger) {
      options.trigger = trigger;
    }
    this.yamcs.yamcsClient
      .getTriggerFirings(this.yamcs.instance!, options)
      .then((firings) => this.firings.set(firings))
      .catch((err) => this.handleError(err));
  }

  private processFiring(firing: TriggerFiring) {
    // Pending confirmations
    const pending = this.pending().filter((f) => f.id !== firing.id);
    if (firing.outcome === PENDING) {
      pending.unshift(firing);
    }
    this.pending.set(pending);

    // Filtered history
    const { outcome, trigger } = this.filterForm.getRawValue();
    const matches =
      (!outcome.length || outcome.includes(firing.outcome)) &&
      (!trigger ||
        trigger === firing.triggerName ||
        trigger === firing.triggerId);
    const firings = [...this.firings()];
    const idx = firings.findIndex((f) => f.id === firing.id);
    if (idx !== -1) {
      if (matches) {
        firings[idx] = firing;
      } else {
        firings.splice(idx, 1);
      }
    } else if (matches) {
      firings.unshift(firing);
    }
    this.firings.set(firings);
  }

  private handleError(err: any) {
    this.messageService.showError(err);
  }

  private updateURL() {
    const { outcome, trigger } = this.filterForm.getRawValue();
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        outcome: outcome.length ? outcome : null,
        trigger: trigger || null,
      },
      queryParamsHandling: 'merge',
    });
  }

  ngOnDestroy() {
    this.firingSubscription?.cancel();
  }
}
