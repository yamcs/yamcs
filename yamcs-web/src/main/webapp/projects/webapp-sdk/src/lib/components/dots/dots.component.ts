import {
  ChangeDetectionStrategy,
  Component,
  input,
  numberAttribute,
  OnDestroy,
  signal,
} from '@angular/core';
import { Subscription } from 'rxjs';
import { BaseComponent } from '../../abc/BaseComponent';

@Component({
  selector: 'ya-dots',
  templateUrl: './dots.component.html',
  styleUrl: './dots.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-dots',
    '[class.connected]': 'isConnected()',
  },
})
export class YaDots extends BaseComponent implements OnDestroy {
  color = input<string>();
  fontSize = input(20, { transform: numberAttribute });

  isConnected = signal(false);

  private subscription: Subscription;

  constructor() {
    super();
    this.subscription = this.yamcs.yamcsClient.connected$.subscribe(
      (connected) => this.isConnected.set(connected),
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
