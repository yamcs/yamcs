import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-monitor-page',
  templateUrl: './MonitorPageTemplate.html',
  styleUrls: ['./MonitorPageTemplate.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MonitorPageTemplate {

  @Input()
  noscroll = false;
}
