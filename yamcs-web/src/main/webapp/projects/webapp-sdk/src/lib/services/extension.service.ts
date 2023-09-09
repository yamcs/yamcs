import { Injectable, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NavGroup, NavItem } from '../navigation';
import { ConfigService } from './config.service';
import { MessageService } from './message.service';
import { YamcsService } from './yamcs.service';

@Injectable({ providedIn: 'root' })
export class ExtensionService {

  readonly configService = inject(ConfigService);
  readonly messageService = inject(MessageService);
  readonly router = inject(Router);
  readonly route = inject(ActivatedRoute);
  readonly yamcs = inject(YamcsService);

  private extraNavItems = new Map<NavGroup, NavItem[]>();

  getExtraNavItems(group: NavGroup) {
    return this.extraNavItems.get(group) || [];
  }

  addNavItem(group: NavGroup, item: NavItem) {
    let items = this.extraNavItems.get(group);
    if (!items) {
      items = [];
      this.extraNavItems.set(group, items);
    }
    items.push(item);
  }
}
