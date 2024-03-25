import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-instance-page',
  templateUrl: './InstancePageTemplate.html',
  styleUrl: './InstancePageTemplate.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstancePageTemplate {

  @Input()
  noscroll = false;
}
