import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AuthService, WebappSdkModule } from '@yamcs/webapp-sdk';
import { OopsComponent } from '../oops/oops.component';

@Component({
  templateUrl: './login-failed.component.html',
  styleUrl: './login-failed.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [OopsComponent, WebappSdkModule],
})
export class LoginFailedComponent {
  private authService = inject(AuthService);

  // Passed as navigation state, rather than query params, so that the
  // message cannot be set from a crafted link.
  private state = (history.state ?? {}) as { message?: string; next?: string };

  readonly message = this.state.message;

  signIn() {
    this.authService.redirectToLogin(this.state.next || '/');
  }
}
