import { Injectable, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NavGroup, NavItem, PageSettings } from '../navigation';
import { AppearanceService } from './appearance.service';
import { ConfigService } from './config.service';
import { MessageService } from './message.service';
import { YamcsService } from './yamcs.service';

@Injectable({ providedIn: 'root' })
export class ExtensionService {
  readonly configService = inject(ConfigService);
  readonly messageService = inject(MessageService);
  readonly appearanceService = inject(AppearanceService);
  readonly router = inject(Router);
  readonly route = inject(ActivatedRoute);
  readonly yamcs = inject(YamcsService);

  private navItems = new Map<NavGroup, NavItem[]>();
  private pageSettingsByExtension = new Map<string, PageSettings>();

  getNavItems(group: NavGroup) {
    const navItems = [...(this.navItems.get(group) || [])];
    navItems.sort((a, b) => {
      const rc = (a.order || 0) - (b.order || 0);
      return rc !== 0 ? rc : a.label.localeCompare(b.label);
    });
    return navItems;
  }

  getPageSettings(extension: string) {
    return this.pageSettingsByExtension.get(extension);
  }

  addNavItem(group: NavGroup, item: NavItem) {
    let items = this.navItems.get(group);
    if (!items) {
      items = [];
      this.navItems.set(group, items);
    }
    items.push(item);
  }

  setPageSettings(extension: string, pageSettings: PageSettings) {
    this.pageSettingsByExtension.set(extension, pageSettings);
  }
}
