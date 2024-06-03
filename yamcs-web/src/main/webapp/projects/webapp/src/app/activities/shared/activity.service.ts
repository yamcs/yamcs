import { Injectable, inject } from '@angular/core';
import { Activity, ActivitySubscription, MessageService, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

/**
 * Shared information about the detail of an activity
 */
@Injectable({
  providedIn: 'root',
})
export class ActivityService {

  private yamcs = inject(YamcsService);
  private messageService = inject(MessageService);

  private activitySubscription?: ActivitySubscription;

  activity$ = new BehaviorSubject<Activity | null>(null);

  connect(activityId: string) {
    const { yamcs } = this;
    let initialReplyReceived = false;

    this.activitySubscription?.cancel();
    this.activitySubscription = yamcs.yamcsClient.createActivitySubscription({
      instance: yamcs.instance!,
    }, activity => {
      if (initialReplyReceived && activity.id === activityId) {
        this.activity$.next(activity);
      }
    });

    yamcs.yamcsClient.getActivity(yamcs.instance!, activityId).then(activity => {
      this.activity$.next(activity);
      initialReplyReceived = true;
    }).catch(err => this.messageService.showError(err));
  }

  disconnect() {
    this.activitySubscription?.cancel();
    this.activitySubscription = undefined;
  }
}
