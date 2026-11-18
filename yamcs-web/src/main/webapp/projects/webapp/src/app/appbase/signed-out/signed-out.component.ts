import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AuthService, WebappSdkModule } from '@yamcs/webapp-sdk';
import { OopsComponent } from '../oops/oops.component';

@Component({
  templateUrl: './signed-out.component.html',
  styleUrl: './signed-out.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [OopsComponent, WebappSdkModule],
})
export class SignedOutComponent {
  private authService = inject(AuthService);

  private state = (history.state ?? {}) as { next?: string };

  signIn() {
    this.authService.redirectToLogin(this.state.next || '/');
  }
}
