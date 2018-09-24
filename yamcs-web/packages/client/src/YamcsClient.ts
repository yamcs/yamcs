import { Observable } from 'rxjs';
import { HttpError } from './HttpError';
import { HttpHandler } from './HttpHandler';
import { HttpInterceptor } from './HttpInterceptor';
import { InstanceClient } from './InstanceClient';
import { BucketsWrapper, InstancesWrapper, ServicesWrapper } from './types/internal';
import { AuthInfo, Bucket, CreateBucketRequest, EditClientRequest, EditInstanceOptions, GeneralInfo, Instance, InstanceSubscriptionResponse, ListObjectsOptions, ListObjectsResponse, Service, TokenResponse, UserInfo } from './types/system';
import { WebSocketClient } from './WebSocketClient';

export default class YamcsClient implements HttpHandler {

  readonly baseUrl = '';
  readonly apiUrl = `${this.baseUrl}/api`;
  readonly authUrl = `${this.baseUrl}/auth`;
  readonly staticUrl = `${this.baseUrl}/static`;

  private accessToken?: string;

  private interceptor: HttpInterceptor;

  public connected$: Observable<boolean>;
  private webSocketClient: WebSocketClient;

  createInstanceClient(instance: string) {
    return new InstanceClient(instance, this);
  }

  async getInstanceUpdates(): Promise<InstanceSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient.getInstanceUpdates();
  }

  /**
   * Returns general auth information. This request
   * does not itself require authenticated access.
   */
  async getAuthInfo() {
    const response = await this.doFetch(`${this.authUrl}`);
    return await response.json() as AuthInfo;
  }

  /**
   * Log in to the Yamcs API.
   * This will return a short-lived access token and an indeterminate refresh token.
   */
  async fetchAccessTokenWithPassword(username: string, password: string) {
    let body = 'grant_type=password';
    body += `&username=${encodeURIComponent(username)}`;
    body += `&password=${encodeURIComponent(password)}`;
    return this.doFetchAccessToken(body);
  }

  async fetchAccessTokenWithAuthorizationCode(authorizationCode: string) {
    let body = 'grant_type=authorization_code';
    body += `&code=${encodeURIComponent(authorizationCode)}`;
    return this.doFetchAccessToken(body);
  }

  async fetchAccessTokenWithRefreshToken(refreshToken: string) {
    let body = 'grant_type=refresh_token';
    body += `&refresh_token=${encodeURIComponent(refreshToken)}`;
    return this.doFetchAccessToken(body);
  }

  /**
   * Set or update the access token for use by this client. Access tokens are short-lived, so you
   * probably have to call this method regularly by first using your refresh token to request
   * a new access token. This client does not automatically refresh for you, as it does not keep
   * track of any issued refresh tokens.
   *
   * In order to handle common token problems, consider adding an In and/or Out Interceptor.
   *
   * - An In Interceptor can prevent requests with expired access tokens. If you still have
   *   access to a refresh token, you can fetch and install a new access token before continuing
   *   the request. Else you may need to ask the user to re-login.
   *
   * - An Out Interceptor can respond to any 401 issues that may still occur. For example,
   *   because an access token was used that is not or no longer accepted by
   *   the server. If you still have access to a refresh token, you can fetch and install
   *   a new access token before re-issuing the request. Else you may need to ask the user
   *   to re-login.
   */
  public setAccessToken(accessToken: string) {
    this.accessToken = accessToken;
  }

  public clearAccessToken() {
    this.accessToken = undefined;
  }

  private async doFetchAccessToken(body: string) {
    const headers = new Headers();
    headers.append('Content-Type', 'application/x-www-form-urlencoded')
    const response = await fetch(`${this.authUrl}/token`, {
      method: 'POST',
      headers,
      body,
    });

    if (response.status >= 200 && response.status < 300) {
      const tokenResponse = await response.json() as TokenResponse;
      return Promise.resolve(tokenResponse);
    } else {
      return Promise.reject(new HttpError(response));
    }
  }

  /**
   * Register an interceptor that will have the opportunity
   * to inspect, modify, halt, or respond to any request.
   */
  setHttpInterceptor(interceptor: HttpInterceptor) {
    this.interceptor = interceptor;
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
    return wrapper.instance || [];
  }

  async getInstance(name: string) {
    const response = await this.doFetch(`${this.apiUrl}/instances/${name}`);
    return await response.json() as Instance;
  }

  async editInstance(name: string, options: EditInstanceOptions) {
    const url = `${this.apiUrl}/instances/${name}`;
    return this.doFetch(url + this.queryString(options), {
      method: 'PATCH',
    });
  }

  async getServices(): Promise<Service[]> {
    const response = await this.doFetch(`${this.apiUrl}/services/_global`);
    const wrapper = await response.json() as ServicesWrapper;
    return wrapper.service || [];
  }

  async getService(name: string): Promise<Service> {
    const url = `${this.apiUrl}/services/_global/${name}`;
    const response = await this.doFetch(url);
    return await response.json() as Service;
  }

  async startService(name: string) {
    const body = JSON.stringify({
      state: 'running'
    });
    return this.doFetch(`${this.apiUrl}/services/_global/${name}`, {
      body,
      method: 'PATCH',
    });
  }

  async stopService(name: string) {
    const body = JSON.stringify({
      state: 'stopped'
    });
    return this.doFetch(`${this.apiUrl}/services/_global/${name}`, {
      body,
      method: 'PATCH',
    });
  }

  async editClient(clientId: number, options: EditClientRequest) {
    const body = JSON.stringify(options);
    const url = `${this.apiUrl}/clients/${clientId}`;
    return await this.doFetch(url, {
      body,
      method: 'PATCH',
    });
  }

  async createBucket(options: CreateBucketRequest) {
    const body = JSON.stringify(options);
    const response = await this.doFetch(`${this.apiUrl}/buckets/_global`, {
      body,
      method: 'POST',
    });
    return await response.json() as Event;
  }

  async getBuckets(): Promise<Bucket[]> {
    const response = await this.doFetch(`${this.apiUrl}/buckets/_global`);
    const wrapper = await response.json() as BucketsWrapper;
    return wrapper.bucket || [];
  }

  async deleteBucket(name: string) {
    return await this.doFetch(`${this.apiUrl}/buckets/_global/${name}`, {
      method: 'DELETE',
    });
  }

  async listObjects(bucket: string, options: ListObjectsOptions = {}): Promise<ListObjectsResponse> {
    const url = `${this.apiUrl}/buckets/_global/${bucket}` + this.queryString(options);
    const response = await this.doFetch(url);
    return await response.json() as ListObjectsResponse;
  }

  async getObject(bucket: string, name: string) {
    return await this.doFetch(this.getObjectURL(bucket, name));
  }

  getObjectURL(bucket: string, name: string) {
    return `${this.apiUrl}/buckets/_global/${bucket}/${name}`;
  }

  async uploadObject(bucket: string, name: string, value: Blob) {
    const url = `${this.apiUrl}/buckets/_global/${bucket}`;
    const formData = new FormData();
    formData.set(name, value, name);
    return await this.doFetch(url, {
      method: 'POST',
      body: formData,
    });
  }

  async deleteObject(bucket: string, name: string) {
    const url = `${this.apiUrl}/buckets/_global/${bucket}/${name}`;
    return await this.doFetch(url, {
      method: 'DELETE',
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

  async doFetch(url: string, init?: RequestInit) {
    if (this.accessToken) {
      if (!init) {
        init = { headers: new Headers() };
      } else if (!init.headers) {
        init.headers = new Headers();
      }
      const headers = init.headers as Headers;
      headers.append('Authorization', `Bearer ${this.accessToken}`);
    }

    let response: Response;
    if (this.interceptor) {
      try {
        response = await this.interceptor(this, url, init);
      } catch (err) {
        return Promise.reject(err);
      }
    } else {
      response = await this.handle(url, init);
    }

    // Make non 2xx responses available to clients via 'catch' instead of 'then'.
    if (response.status >= 200 && response.status < 300) {
      return Promise.resolve(response);
    } else {
      return Promise.reject(new HttpError(response));
    }
  }

  handle(url: string, init?: RequestInit) {
    // Our handler uses Fetch API, available in modern browsers.
    // For older browsers, the end application should include an
    // appropriate polyfill.
    return fetch(url, init);
  }

  private prepareWebSocketClient() {
    if (!this.webSocketClient) {
      this.webSocketClient = new WebSocketClient();
      this.connected$ = this.webSocketClient.connected$;
    }
  }

  closeConnection() {
    if (this.webSocketClient) {
      this.webSocketClient.close();
    }
  }

  private queryString(options: {[key: string]: any}) {
    const qs = Object.keys(options)
      .map(k => `${k}=${options[k]}`)
      .join('&');
    return qs === '' ? qs : '?' + qs;
  }
}
