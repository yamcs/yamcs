import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Container } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

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
