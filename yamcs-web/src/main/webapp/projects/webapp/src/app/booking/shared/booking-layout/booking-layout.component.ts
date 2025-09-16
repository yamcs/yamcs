import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import {
  AuthService,
  ConfigService,
  User,
  WebappSdkModule,
  WebsiteConfig,
} from '@yamcs/webapp-sdk';
import { Subscription, filter } from 'rxjs';

@Component({
  templateUrl: './booking-layout.component.html',
  styleUrl: './booking-layout.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class BookingLayoutComponent implements OnDestroy {
  user: User;
  config: WebsiteConfig;

  private routerSubscription: Subscription;

  constructor(
    configService: ConfigService,
    authService: AuthService,
    router: Router,
  ) {
    this.config = configService.getConfig();
    this.user = authService.getUser()!;

    this.routerSubscription = router.events
      .pipe(filter((evt) => evt instanceof NavigationEnd))
      .subscribe((evt: any) => {
        // Handle any booking-specific navigation updates here
      });
  }

  ngOnDestroy() {
    this.routerSubscription?.unsubscribe();
  }
}