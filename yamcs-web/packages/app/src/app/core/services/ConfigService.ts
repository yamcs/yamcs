import { Injectable } from '@angular/core';
import { WebsiteConfig, YamcsClient } from '@yamcs/client';

@Injectable()
export class ConfigService {

  private websiteConfig: WebsiteConfig;

  async loadWebsiteConfig() {
    const client = new YamcsClient();
    return client.getWebsiteConfig().then(websiteConfig => {
      this.websiteConfig = websiteConfig;
    });
  }

  getAuthInfo() {
    return this.websiteConfig.auth;
  }

  getDisplayScope() {
    return this.websiteConfig.displayScope;
  }

  getStackScope() {
    return this.websiteConfig.stackScope;
  }
}
