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

  createInstanceClient(instance: string) {
    return new InstanceClient(instance, this);
  }

  async getGeneralInfo() {
    const response = await fetch(this.apiUrl).then(this.verifyStatus);
    return await response.json() as GeneralInfo;
  }

  /**
   * Returns info on the authenticated user
   */
  async getUserInfo() {
    const response = await fetch(`${this.apiUrl}/user`).then(this.verifyStatus);
    return await response.json() as UserInfo;
  }

  async getInstances() {
    const response = await fetch(`${this.apiUrl}/instances`).then(this.verifyStatus);
    const wrapper = await response.json() as InstancesWrapper;
    return wrapper.instance;
  }

  async getInstance(name: string) {
    const response = await fetch(`${this.apiUrl}/instances/${name}`).then(this.verifyStatus);
    return await response.json() as Instance;
  }

  async getServices() {
    const response = await fetch(`${this.apiUrl}/services/_global`).then(this.verifyStatus);
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
    }).then(this.verifyStatus);
  }

  async stopService(name: string) {
    const body = JSON.stringify({
      state: 'stopped'
    })
    return fetch(`${this.apiUrl}/services/_global/service/${name}`, {
      body,
      method: 'PATCH',
    }).then(this.verifyStatus);
  }

  async getStaticText(path: string) {
    const response = await fetch(`${this.staticUrl}/${path}`).then(this.verifyStatus);
    return await response.text()
  }

  async getStaticXML(path: string) {
    const response = await fetch(`${this.staticUrl}/${path}`).then(this.verifyStatus);
    const text = await response.text();
    const xmlParser = new DOMParser();
    return xmlParser.parseFromString(text, 'text/xml') as XMLDocument;
  }

  // Make non 2xx responses available to clients via 'catch' instead of 'then'.
  private verifyStatus(response: Response) {
    if (response.status >= 200 && response.status < 300) {
      return Promise.resolve(response)
    } else {
      return Promise.reject(new Error(response.statusText))
    }
  }
}
