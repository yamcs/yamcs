import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Placeholder just to work around being able to capture file paths via
 * angular router '**' wildcard, rather than being forced to use
 * query parameters.
 */

@Component({
  standalone: true,
  selector: 'app-bucket-placeholder',
  template: '<router-outlet />',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet,
  ],
})
export class BucketPlaceholderComponent {
}
