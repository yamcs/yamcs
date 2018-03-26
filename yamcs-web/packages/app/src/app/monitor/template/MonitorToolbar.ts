import {
  ChangeDetectionStrategy,
  Component,
} from '@angular/core';

@Component({
  selector: 'app-monitor-toolbar',
  templateUrl: './MonitorToolbar.html',
  styleUrls: ['./MonitorToolbar.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MonitorToolbar {
}
