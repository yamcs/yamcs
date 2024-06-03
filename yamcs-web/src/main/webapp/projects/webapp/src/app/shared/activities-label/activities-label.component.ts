import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { GlobalActivityStatus, GlobalActivityStatusSubscription, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';

@Component({
  standalone: true,
  selector: 'app-activities-label',
  templateUrl: './activities-label.component.html',
  styleUrl: './activities-label.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class ActivitiesLabelComponent implements OnDestroy {

  private connectionInfoSubscription: Subscription;

  context$ = new BehaviorSubject<string | null>(null);
  status$ = new BehaviorSubject<GlobalActivityStatus | null>(null);

  private statusSubscription: GlobalActivityStatusSubscription;

  constructor(
    readonly yamcs: YamcsService,
    authService: AuthService,
  ) {
    this.connectionInfoSubscription = yamcs.connectionInfo$.subscribe(connectionInfo => {
      if (connectionInfo && connectionInfo.instance) {
        let context = connectionInfo.instance.name;
        if (connectionInfo.processor) {
          if (authService.getUser()!.hasSystemPrivilege('ReadActivities')) {
            const options = {
              instance: connectionInfo.instance.name,
            };
            this.statusSubscription = yamcs.yamcsClient.createGlobalActivityStatusSubscription(options, status => {
              this.status$.next(status);
            });
          }
          context += ';' + connectionInfo.processor;
        }
        this.context$.next(context);
      } else {
        this.clearActivitySubscription();
        this.context$.next(null);
      }
    });
  }

  private clearActivitySubscription() {
    this.statusSubscription?.cancel();
    this.status$.next(null);
  }

  ngOnDestroy() {
    this.clearActivitySubscription();
    this.connectionInfoSubscription?.unsubscribe();
  }
}
