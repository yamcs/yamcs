import { Injectable } from '@angular/core';
import { AuthInfo } from '@yamcs/client';

export interface WebsiteConfig {
  serverId: string;
  auth: AuthInfo;
  tag: string;
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
}
