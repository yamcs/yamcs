import { Injectable } from '@angular/core';
import { AuthInfo, CommandOption } from '../../client';
import { ColumnInfo } from '../../shared/template/ColumnChooser';
import { User } from '../../shared/User';

export interface WebsiteConfig {
  serverId: string;
  auth: AuthInfo;
  tag: string;
  features: FeaturesConfig;
  events?: EventsConfig;
  commandClearances: boolean;
  twoStageCommanding: boolean;
  commandOptions: CommandOption[];
  completeness: boolean;
}

export interface FeaturesConfig {
  cfdp: boolean;
  dass: boolean;
  tc: boolean;
  tmArchive: boolean;
}

export interface EventsConfig {
  extraColumns?: ExtraColumnInfo[];
}

export type NavGroup = 'telemetry' | 'commanding' | 'archive' | 'mdb';

export interface NavItem {
  path: string;
  label: string;
  condition?: (user: User) => boolean;
}

export interface ExtraColumnInfo extends ColumnInfo {
  /**
   * id of another column after which to insert this column.
   * This only impacts the ordering in the column chooser dropdown.
   *
   * Typically you want to set this so that the ordering matches
   * the configured ordering of 'displayedColumns'.
   */
  after: string;
}

@Injectable()
export class ConfigService {

  private websiteConfig: WebsiteConfig;
  private extraNavItems = new Map<NavGroup, NavItem[]>();

  async loadWebsiteConfig() {
    const el = document.getElementById('appConfig')!;
    this.websiteConfig = JSON.parse(el.innerText);
  }

  getServerId() {
    return this.websiteConfig.serverId;
  }

  getAuthInfo() {
    return this.websiteConfig.auth;
  }

  getTag() {
    return this.websiteConfig.tag;
  }

  getCommandOptions() {
    return this.websiteConfig.commandOptions;
  }

  getConfig() {
    return this.websiteConfig;
  }

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
