import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-admin-page',
  templateUrl: './AdminPageTemplate.html',
  styleUrl: './AdminPageTemplate.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminPageTemplate {

  @Input()
  noscroll = false;
}
