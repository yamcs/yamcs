import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { Value } from '../../client';

@Component({
  selector: 'app-value',
  templateUrl: './ValueComponent.html',
  styleUrls: ['./ValueComponent.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ValueComponent {

  @Input()
  value?: Value;

  collapsed$ = new BehaviorSubject<boolean>(true);
}
