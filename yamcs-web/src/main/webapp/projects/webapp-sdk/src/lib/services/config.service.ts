import { Injectable } from '@angular/core';
import { AuthInfo, CommandOption, InstanceConfig } from '../client';
import { YaColumnInfo } from '../components/column-chooser/column-chooser.component';

export interface WebsiteConfig {
  serverId: string;
  auth: AuthInfo;
  tag: string;
  logo: string;
  plugins: string[];
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
  cookie: CookieConfig;
  dass: boolean;
  tc: boolean;
  tmArchive: boolean;
  utc: boolean;
  siteLinks: SiteLink[];
  extra: { [key: string]: { [key: string]: any; }; };
}

export interface EventsConfig {
  extraColumns?: ExtraColumnInfo[];
}

export interface CookieConfig {
  secure: boolean;
  sameSite: string;
}

export interface SiteLink {
  label: string;
  url: string;
  external: boolean;
}

export interface ExtraColumnInfo extends YaColumnInfo {
  /**
   * id of another column after which to insert this column.
   * This only impacts the ordering in the column chooser dropdown.
   *
   * Typically you want to set this so that the ordering matches
   * the configured ordering of 'displayedColumns'.
   */
  after: string;
}

@Injectable({ providedIn: 'root' })
export class ConfigService {

  private websiteConfig: WebsiteConfig;
  private instanceConfig: InstanceConfig;

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

  getPluginIds() {
    return this.websiteConfig.plugins;
  }

  getDisplayBucket() {
    return this.instanceConfig.displayBucket;
  }

  getStackBucket() {
    return this.instanceConfig.stackBucket;
  }

  isParameterArchiveEnabled() {
    return this.instanceConfig.parameterArchive;
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

  getExtraConfig(key: string) {
    if (this.websiteConfig.extra.hasOwnProperty(key)) {
      return this.websiteConfig.extra[key];
    }
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
}
