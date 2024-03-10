import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Activity, MessageService, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

@Component({
  templateUrl: './ActivityDetailsTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActivityDetailsTab {

  activity$ = new BehaviorSubject<Activity | null>(null);

  constructor(
    route: ActivatedRoute,
    readonly yamcs: YamcsService,
    messageService: MessageService,
  ) {
    const activityId = route.parent!.snapshot.paramMap.get('activityId')!;
    yamcs.yamcsClient.getActivity(yamcs.instance!, activityId).then(activity => {
      this.activity$.next(activity);
    }).catch(err => messageService.showError(err));
  }
}
