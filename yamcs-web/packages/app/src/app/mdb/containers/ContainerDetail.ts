import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Container, Instance } from '@yamcs/client';

@Component({
  selector: 'app-container-detail',
  templateUrl: './ContainerDetail.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContainerDetail {

  @Input()
  instance: Instance;

  @Input()
  container: Container;
}
