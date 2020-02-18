import { Injectable } from '@angular/core';
import { AuthInfo } from '../../client';
import { ColumnInfo } from '../../shared/template/ColumnChooser';

export interface WebsiteConfig {
  serverId: string;
  auth: AuthInfo;
  tag: string;
  features: FeaturesConfig;
  events?: EventsConfig;
  commandClearances: boolean;
  twoStageCommanding: boolean;
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

  getConfig() {
    return this.websiteConfig;
  }
}
