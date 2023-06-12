import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { StreamData } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-stream-data',
  templateUrl: './StreamDataComponent.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StreamDataComponent {

  @Input()
  streamData: StreamData;
}
