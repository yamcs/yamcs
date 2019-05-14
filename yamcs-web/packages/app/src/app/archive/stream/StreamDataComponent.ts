import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { StreamData } from '@yamcs/client';

@Component({
  selector: 'app-stream-data',
  templateUrl: './StreamDataComponent.html',
  styleUrls: ['./StreamDataComponent.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StreamDataComponent {

  @Input()
  streamData: StreamData;
}
