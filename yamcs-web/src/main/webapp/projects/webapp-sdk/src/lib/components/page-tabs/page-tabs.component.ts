import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';

@Component({
  selector: 'ya-page-tabs',
  templateUrl: './page-tabs.component.html',
  styleUrl: './page-tabs.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-page-tabs',
  },
  imports: [MatTabsModule],
})
export class YaPageTabs implements OnInit, OnDestroy {
  elementRef = inject(ElementRef);

  selectedIndex: number | undefined = undefined;

  private observer?: MutationObserver;

  ngOnInit(): void {
    const el = this.elementRef.nativeElement;

    this.observer = new MutationObserver(() => {
      let selectedIndex = undefined;
      for (let i = 0; i < el.children.length; i++) {
        const child = el.children.item(i);
        if (child?.tagName === 'A') {
          const tabEl = child as HTMLAnchorElement;
          if (tabEl.classList.contains('active')) {
            selectedIndex = i;
          }
        } else {
          break;
        }
      }
      this.selectedIndex = selectedIndex;
    });

    this.observer.observe(el, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['class'],
    });
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }
}
