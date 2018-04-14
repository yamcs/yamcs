import {
  InstancesWrapper,
  ServicesWrapper,
} from './types/internal';

import {
  GeneralInfo,
  Instance,
  Service,
  UserInfo,
  AccessTokenResponse,
} from './types/system';

import { InstanceClient } from './InstanceClient';
import { access } from 'fs';
import { RequestListener } from './RequestListener';
import { HttpError } from './HttpError';

export default class YamcsClient {

  readonly baseUrl = '';
  readonly apiUrl = `${this.baseUrl}/api`;
  readonly staticUrl = `${this.baseUrl}/_static`;

  public accessToken?: string;

  private requestListeners: (RequestListener)[] = [];

  createInstanceClient(instance: string) {
    return new InstanceClient(instance, this);
  }

  /**
   * Log in to the Yamcs API via a username and a password.
   * This will return a JWT reponse with a certain
   * expiration time.
   */
  async login(username: string, password: string) {
    const headers = new Headers();
    headers.append('Content-Type', 'application/x-www-form-urlencoded')

    let body = `grant_type=password`;
    body += `&username=${encodeURIComponent(username)}`;
    body += `&password=${encodeURIComponent(password)}`;

    const response = await fetch(`${this.apiUrl}/token`, {
      method: 'POST',
      headers,
      body,
    });

    if (response.status >= 200 && response.status < 300) {
      const tokenResponse = await response.json() as AccessTokenResponse;
      this.accessToken = tokenResponse.access_token;
      return Promise.resolve(tokenResponse);
    } else {
      return Promise.reject(new HttpError(response.status, response.statusText));
    }
  }

  /**
   * Uses the current access token to request a new access token that
   * will be used in subsequent requests. If the original access token
   * was already expired, this will fail.
   */
  async refreshAccessToken() {
    const response = await this.doFetch(`${this.apiUrl}/token`);
    const tokenResponse = await response.json() as AccessTokenResponse;
    this.accessToken = tokenResponse.access_token;
    return tokenResponse;
  }

  /**
   * Register a listener that will get updated before every request.
   * This does not provide any intercepting capabilities. Neither
   * does it say anything about the http response status.
   */
  addRequestListener(requestListener: RequestListener) {
    this.requestListeners.push(requestListener);
  }

  async getGeneralInfo() {
    const response = await this.doFetch(this.apiUrl);
    return await response.json() as GeneralInfo;
  }

  /**
   * Returns info on the authenticated user
   */
  async getUserInfo() {
    const response = await this.doFetch(`${this.apiUrl}/user`);
    return await response.json() as UserInfo;
  }

  async getInstances() {
    const response = await this.doFetch(`${this.apiUrl}/instances`);
    const wrapper = await response.json() as InstancesWrapper;
    return wrapper.instance;
  }

  async getInstance(name: string) {
    const response = await this.doFetch(`${this.apiUrl}/instances/${name}`);
    return await response.json() as Instance;
  }

  async getServices() {
    const response = await this.doFetch(`${this.apiUrl}/services/_global`);
    const wrapper = await response.json() as ServicesWrapper;
    return wrapper.service || [];
  }

  async startService(name: string) {
    const body = JSON.stringify({
      state: 'running'
    })
    return this.doFetch(`${this.apiUrl}/services/_global/service/${name}`, {
      body,
      method: 'PATCH',
    });
  }

  async stopService(name: string) {
    const body = JSON.stringify({
      state: 'stopped'
    })
    return this.doFetch(`${this.apiUrl}/services/_global/service/${name}`, {
      body,
      method: 'PATCH',
    });
  }

  async getStaticText(path: string) {
    const response = await this.doFetch(`${this.staticUrl}/${path}`);
    return await response.text();
  }

  async getStaticXML(path: string) {
    const response = await this.doFetch(`${this.staticUrl}/${path}`);
    const text = await response.text();
    const xmlParser = new DOMParser();
    return xmlParser.parseFromString(text, 'text/xml') as XMLDocument;
  }

  async doFetch(input?: any, init?: RequestInit) {
    if (this.accessToken) {
      if (!init) {
        init = { headers: new Headers() };
      } else if (!init.headers) {
        init.headers = new Headers();
      }
      const headers = init.headers as Headers;
      headers.append('Authorization', `Bearer ${this.accessToken}`);
    }
    for (const requestListener of this.requestListeners) {
      requestListener(input);
    }
    return fetch(input, init).then(response => {
      // Make non 2xx responses available to clients via 'catch' instead of 'then'.
      if (response.status >= 200 && response.status < 300) {
        return Promise.resolve(response);
      } else {
        return Promise.reject(new HttpError(response.status, response.statusText));
      }
    });
  }
}
