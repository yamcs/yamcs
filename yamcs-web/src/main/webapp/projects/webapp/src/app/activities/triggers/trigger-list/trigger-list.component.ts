import { Component, inject, OnDestroy, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import {
  AuthService,
  MessageService,
  Trigger,
  TriggerFiringSubscription,
  TriggerServiceStatus,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { Subject, debounceTime } from 'rxjs';
import { ActivitiesPageTabsComponent } from '../shared/activities-page-tabs.component';
import {
  activitySummary,
  conditionSummary,
  mayEditTriggers,
  mayToggleTriggers,
} from '../shared/triggers';

@Component({
  templateUrl: './trigger-list.component.html',
  styleUrl: './trigger-list.component.css',
  imports: [ActivitiesPageTabsComponent, WebappSdkModule],
})
export class TriggerListComponent implements OnDestroy {
  readonly yamcs = inject(YamcsService);
  private authService = inject(AuthService);
  private messageService = inject(MessageService);

  triggers = signal<Trigger[]>([]);
  status = signal<TriggerServiceStatus | null>(null);

  displayedColumns = [
    'enabled',
    'name',
    'condition',
    'activity',
    'lastFired',
    'firingCount',
    'actions',
  ];

  conditionSummary = conditionSummary;
  activitySummary = activitySummary;

  private firingSubscription?: TriggerFiringSubscription;
  private refresh$ = new Subject<void>();

  constructor() {
    inject(Title).setTitle('Triggers');
    this.refresh();

    this.refresh$.pipe(debounceTime(500)).subscribe(() => this.refresh());
    this.firingSubscription =
      this.yamcs.yamcsClient.createTriggerFiringSubscription(
        { instance: this.yamcs.instance! },
        () => this.refresh$.next(),
      );
  }

  mayToggle() {
    return mayToggleTriggers(this.authService);
  }

  mayEdit() {
    return mayEditTriggers(this.authService);
  }

  refresh() {
    const { yamcs } = this;
    Promise.all([
      yamcs.yamcsClient.getTriggerServiceStatus(yamcs.instance!),
      yamcs.yamcsClient.getTriggers(yamcs.instance!),
    ])
      .then(([status, triggers]) => {
        this.status.set(status);
        this.triggers.set(triggers);
      })
      .catch((err) => this.messageService.showError(err));
  }

  setGlobalEnabled(enabled: boolean) {
    const { yamcs } = this;
    const promise = enabled
      ? yamcs.yamcsClient.enableAllTriggers(yamcs.instance!)
      : yamcs.yamcsClient.disableAllTriggers(yamcs.instance!);
    promise
      .then((status) => this.status.set(status))
      .catch((err) => {
        this.messageService.showError(err);
        this.refresh();
      });
  }

  setEnabled(trigger: Trigger, enabled: boolean) {
    const { yamcs } = this;
    const promise = enabled
      ? yamcs.yamcsClient.enableTrigger(yamcs.instance!, trigger.id)
      : yamcs.yamcsClient.disableTrigger(yamcs.instance!, trigger.id);
    promise
      .then((updated) => this.replaceTrigger(updated))
      .catch((err) => {
        this.messageService.showError(err);
        this.refresh();
      });
  }

  deleteTrigger(trigger: Trigger) {
    if (
      !confirm(`Are you sure you want to delete trigger '${trigger.name}'?`)
    ) {
      return;
    }
    const { yamcs } = this;
    yamcs.yamcsClient
      .deleteTrigger(yamcs.instance!, trigger.id)
      .then(() => this.refresh())
      .catch((err) => this.messageService.showError(err));
  }

  private replaceTrigger(updated: Trigger) {
    this.triggers.set(
      this.triggers().map((t) => (t.id === updated.id ? updated : t)),
    );
  }

  ngOnDestroy() {
    this.firingSubscription?.cancel();
    this.refresh$.complete();
  }
}
