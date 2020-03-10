import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Container } from '../../client';

@Component({
  selector: 'app-container-detail',
  templateUrl: './ContainerDetail.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContainerDetail {

  @Input()
  instance: string;

  @Input()
  container: Container;
}
