import { booleanAttribute, ChangeDetectionStrategy, Component, input, OnDestroy, OnInit, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { BaseComponent } from '../../abc/BaseComponent';

@Component({
  selector: 'ya-detail-pane',
  templateUrl: './detail-pane.component.html',
  styleUrl: './detail-pane.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YaDetailPane extends BaseComponent implements OnInit, OnDestroy {

  alwaysOpen = input(false, { transform: booleanAttribute });
  closed = signal(true);

  private detailPaneSubscription?: Subscription;

  ngOnInit(): void {
    this.detailPaneSubscription = this.appearanceService.detailPane$.subscribe(opened => {
      this.closed.set(!opened);
    });
  }

  ngOnDestroy(): void {
    this.detailPaneSubscription?.unsubscribe();
    this.closeDetailPane(); // Forget state
  }
}
