import { Injectable } from '@angular/core';
import { WebsiteConfig, YamcsClient } from '@yamcs/client';
import { YamcsService } from './YamcsService';

@Injectable()
export class ConfigService {

  private websiteConfig: WebsiteConfig;

  constructor(private yamcs: YamcsService) {
  }

  async loadWebsiteConfig() {
    const client = new YamcsClient();
    return client.getWebsiteConfig().then(websiteConfig => {
      this.websiteConfig = websiteConfig;
    });
  }

  getAuthInfo() {
    return this.websiteConfig.auth;
  }

  getDisplayBucketInstance() {
    const instance = this.yamcs.getInstance();
    if (!instance || this.websiteConfig.displayScope === 'GLOBAL') {
      return '_global';
    } else {
      return instance.name;
    }
  }

  getStackBucketInstance() {
    const instance = this.yamcs.getInstance();
    if (!instance || this.websiteConfig.stackScope === 'GLOBAL') {
      return '_global';
    } else {
      return instance.name;
    }
  }
}
