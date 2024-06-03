import { ChangeDetectionStrategy, Component, OnInit, input } from '@angular/core';
import { Activity, MessageService, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

@Component({
  standalone: true,
  templateUrl: './activity-details-tab.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class ActivityDetailsTabComponent implements OnInit {

  activityId = input.required<string>();
  activity$ = new BehaviorSubject<Activity | null>(null);

  constructor(
    readonly yamcs: YamcsService,
    private messageService: MessageService,
  ) { }

  ngOnInit() {
    const { yamcs } = this;
    yamcs.yamcsClient.getActivity(yamcs.instance!, this.activityId()).then(activity => {
      this.activity$.next(activity);
    }).catch(err => this.messageService.showError(err));
  }
}
