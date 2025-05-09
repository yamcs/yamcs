import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { StreamData, ValuePipe, YaAttr, YaAttrList } from '@yamcs/webapp-sdk';
import { HexComponent } from '../../../shared/hex/hex.component';

@Component({
  selector: 'app-stream-data',
  templateUrl: './stream-data.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [HexComponent, ValuePipe, YaAttr, YaAttrList],
})
export class StreamDataComponent {
  @Input()
  streamData: StreamData;
}
