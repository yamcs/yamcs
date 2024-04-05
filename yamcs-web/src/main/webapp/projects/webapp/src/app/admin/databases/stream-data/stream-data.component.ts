import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { StreamData, WebappSdkModule } from '@yamcs/webapp-sdk';
import { HexComponent } from '../../../shared/hex/hex.component';

@Component({
  standalone: true,
  selector: 'app-stream-data',
  templateUrl: './stream-data.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    HexComponent,
    WebappSdkModule,
  ],
})
export class StreamDataComponent {

  @Input()
  streamData: StreamData;
}
