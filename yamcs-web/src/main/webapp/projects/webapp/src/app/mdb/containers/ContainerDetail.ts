import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Container, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-container-detail',
  templateUrl: './ContainerDetail.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContainerDetail {

  @Input()
  container: Container;

  constructor(readonly yamcs: YamcsService) {
  }
}
