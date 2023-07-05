import { Injectable } from '@angular/core';
import { AuthInfo, ColumnInfo, CommandOption, InstanceConfig, User } from '@yamcs/webapp-sdk';

export interface WebsiteConfig {
  serverId: string;
  auth: AuthInfo;
  tag: string;
  logo: string;
  events?: EventsConfig;
  commandClearanceEnabled: boolean;
  commandExports: boolean;
  twoStageCommanding: boolean;
  preferredNamespace: string;
  collapseInitializedArguments: boolean;
  commandOptions: CommandOption[];
  queueNames: string[];
  hasTemplates: boolean;
  disableLoginForm: boolean;
  logoutRedirectUrl: string;
  dass: boolean;
  tc: boolean;
  tmArchive: boolean;
  displayFolderPerInstance: boolean;
  stackFolderPerInstance: boolean;
  siteLinks: SiteLink[];
}

export interface EventsConfig {
  extraColumns?: ExtraColumnInfo[];
}

export type NavGroup = 'telemetry' | 'commanding' | 'archive' | 'mdb';

export interface NavItem {
  path: string;
  label: string;
  icon?: string;
  condition?: (user: User) => boolean;
}

export interface SiteLink {
  label: string;
  url: string;
  external: boolean;
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
  private instanceConfig: InstanceConfig;
  private extraNavItems = new Map<NavGroup, NavItem[]>();

  async loadWebsiteConfig() {
    const el = document.getElementById('appConfig')!;
    this.websiteConfig = JSON.parse(el.innerText);
    return this.websiteConfig;
  }

  getServerId() {
    return this.websiteConfig.serverId;
  }

  getAuthInfo() {
    return this.websiteConfig.auth;
  }

  getDisplayBucket() {
    return this.instanceConfig.displayBucket;
  }

  getStackBucket() {
    return this.instanceConfig.stackBucket;
  }

  getTag() {
    return this.websiteConfig.tag;
  }

  getCommandOptions() {
    return this.websiteConfig.commandOptions;
  }

  hasTemplates() {
    return this.websiteConfig.hasTemplates;
  }

  getDisableLoginForm() {
    return this.websiteConfig.disableLoginForm;
  }

  getConfig() {
    return this.websiteConfig;
  }

  setInstanceConfig(instanceConfig: InstanceConfig) {
    this.instanceConfig = instanceConfig;
  }

  getSiteLinks() {
    return this.websiteConfig.siteLinks || [];
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
