import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  OnDestroy,
  OnInit,
  signal,
} from '@angular/core';
import { Subscription } from 'rxjs';
import { BaseComponent } from '../../abc/BaseComponent';

@Component({
  selector: 'ya-detail-pane',
  template: '<ng-content />',
  styleUrl: './detail-pane.component.css',
  host: {
    class: 'ya-detail-pane',
    '[class.hidden]': 'hidden()',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YaDetailPane extends BaseComponent implements OnInit, OnDestroy {
  alwaysOpen = input(false, { transform: booleanAttribute });
  closed = signal(true);

  hidden = computed(() => {
    return !this.alwaysOpen() && this.closed();
  });

  private detailPaneSubscription?: Subscription;

  ngOnInit(): void {
    this.detailPaneSubscription = this.appearanceService.detailPane$.subscribe(
      (opened) => {
        this.closed.set(!opened);
      },
    );
  }

  ngOnDestroy(): void {
    this.detailPaneSubscription?.unsubscribe();
    this.closeDetailPane(); // Forget state
  }
}
