import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  signal,
} from '@angular/core';
import { ConfigService, WebsiteConfig } from '../../services/config.service';
import { Preferences } from '../../services/preferences.service';
import { YaCollapseSidebar } from './collapse-sidebar.component';

@Component({
  selector: 'ya-sidenav',
  templateUrl: './sidenav.component.html',
  styleUrl: './sidenav.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-sidebar',
    '[class.collapsed]': 'collapsed()',
    '[class.hover]': 'hover()',
    '[class.no-transition]': '!pageLoaded()',
    '[class.over]': 'over()',
    '(mouseenter)': 'hover.set(true)',
    '(mouseleave)': 'hover.set(false)',
    '(click)': 'onClick($event)',
  },
  imports: [YaCollapseSidebar],
})
export class YaSidenav implements AfterViewInit {
  collapsed = input.required<boolean>();

  pageLoaded = signal(false);
  hover = signal(false);

  // In 'over' mode, the main content does not shift, the
  // sidenav is still considered 'mini', but renders in-full
  // over the content.
  over = computed(() => this.collapsed() && this.hover());
  collapseItem = computed(() => this.collapsed() && !this.hover());

  config: WebsiteConfig;

  constructor(
    configService: ConfigService,
    private prefs: Preferences,
  ) {
    this.config = configService.getConfig();
  }

  ngAfterViewInit(): void {
    // Avoid sidebar FOUC when actually collapsed
    requestAnimationFrame(() => this.pageLoaded.set(true));
  }

  toggleCollapse() {
    const collapsed = !this.collapsed();
    this.prefs.setBoolean('sidenav.collapsed', collapsed);
    if (collapsed) {
      this.hover.set(false); // Close sidebar even if hovered
    }
  }

  onClick(event: MouseEvent) {
    if (!event.target) {
      return;
    }

    // If an item is clicked, close sidebar even if hovered
    if ((event.target as HTMLElement).closest('ya-sidenav-item')) {
      if (this.collapsed()) {
        this.hover.set(false);
      }
    }
  }
}
