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

  getGeneralInfo() {
    return fetch(this.apiUrl)
      .then(res => res.json() as Promise<GeneralInfo>);
  }

  /**
   * Returns info on the authenticated user
   */
  getUserInfo() {
    return fetch(`${this.apiUrl}/user`)
      .then(res => res.json() as Promise<UserInfo>);
  }

  getInstances() {
    return fetch(`${this.apiUrl}/instances`)
      .then(res => res.json() as Promise<InstancesWrapper>)
      .then(wrapper => wrapper.instance)
  }

  getInstance(name: string) {
    return fetch(`${this.apiUrl}/instances/${name}`)
      .then(res => res.json() as Promise<Instance>);
  }

  getServices() {
    return fetch(`${this.apiUrl}/services/_global`)
      .then(res => res.json() as Promise<ServicesWrapper>)
      .then(wrapper => wrapper.service || []);
  }

  /*startService(name: string) {
    return this.http.patch(`${this.apiUrl}/services/_global/service/${name}`, {
      state: 'running'
    });
  }

  stopService(name: string) {
    return this.http.patch(`${this.apiUrl}/services/_global/${name}`, {
      state: 'stopped'
    });
  }*/

  getStaticText(path: string) {
    return fetch(`${this.staticUrl}/${path}`)
      .then(res => res.text());
  }

  getStaticXML(path: string) {
    return fetch(`${this.staticUrl}/${path}`)
      .then(res => res.text())
      .then(text => {
        const xmlParser = new DOMParser();
        const doc = xmlParser.parseFromString(text, 'text/xml');
        return doc as XMLDocument;
      });
  }
}
