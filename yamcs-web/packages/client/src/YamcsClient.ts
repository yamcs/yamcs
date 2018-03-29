import {
  InstancesWrapper,
  ServicesWrapper,
} from './types/internal';

import {
  GeneralInfo,
  Instance,
  Service,
  UserInfo,
} from './types/system';

import { InstanceClient } from './InstanceClient';

export default class YamcsClient {

  readonly baseUrl = '';
  readonly apiUrl = `${this.baseUrl}/api`;
  readonly staticUrl = `${this.baseUrl}/_static`;

  selectInstance(instance: string) {
    return new InstanceClient(instance, this);
  }

  async getGeneralInfo() {
    const response = await fetch(this.apiUrl);
    return await response.json() as GeneralInfo;
  }

  /**
   * Returns info on the authenticated user
   */
  async getUserInfo() {
    const response = await fetch(`${this.apiUrl}/user`);
    return await response.json() as UserInfo;
  }

  async getInstances() {
    const response = await fetch(`${this.apiUrl}/instances`);
    const wrapper = await response.json() as InstancesWrapper;
    return wrapper.instance;
  }

  async getInstance(name: string) {
    const response = await fetch(`${this.apiUrl}/instances/${name}`);
    return await response.json() as Instance;
  }

  async getServices() {
    const response = await fetch(`${this.apiUrl}/services/_global`);
    const wrapper = await response.json() as ServicesWrapper;
    return wrapper.service || [];
  }

  async startService(name: string) {
    const body = JSON.stringify({
      state: 'running'
    })
    return fetch(`${this.apiUrl}/services/_global/service/${name}`, {
      body,
      method: 'PATCH',
    });
  }

  async stopService(name: string) {
    const body = JSON.stringify({
      state: 'stopped'
    })
    return fetch(`${this.apiUrl}/services/_global/service/${name}`, {
      body,
      method: 'PATCH',
    });
  }

  async getStaticText(path: string) {
    const response = await fetch(`${this.staticUrl}/${path}`);
    return await response.text()
  }

  async getStaticXML(path: string) {
    const response = await fetch(`${this.staticUrl}/${path}`);
    const text = await response.text();
    const xmlParser = new DOMParser();
    return xmlParser.parseFromString(text, 'text/xml') as XMLDocument;
  }
}
