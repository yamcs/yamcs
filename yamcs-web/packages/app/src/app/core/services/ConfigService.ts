import { APP_BASE_HREF } from '@angular/common';
import { Inject, Injectable } from '@angular/core';
import { WebsiteConfig, YamcsClient } from '@yamcs/client';

@Injectable()
export class ConfigService {

  private websiteConfig: WebsiteConfig;

  constructor(@Inject(APP_BASE_HREF) private baseHref: string) {
  }

  async loadWebsiteConfig() {
    const client = new YamcsClient(this.baseHref);
    return client.getWebsiteConfig().then(websiteConfig => {
      this.websiteConfig = websiteConfig;
    });
  }

  getAuthInfo() {
    return this.websiteConfig.auth;
  }

  getTag() {
    return this.websiteConfig.tag;
  }
}
