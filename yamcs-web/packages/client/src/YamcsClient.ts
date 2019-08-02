import { Observable } from 'rxjs';
import { HttpError } from './HttpError';
import { HttpHandler } from './HttpHandler';
import { HttpInterceptor } from './HttpInterceptor';
import { InstanceClient } from './InstanceClient';
import { ClientsWrapper, InstancesWrapper, InstanceTemplatesWrapper, RocksDbDatabasesWrapper, RolesWrapper, ServicesWrapper, UsersWrapper } from './types/internal';
import { AuthInfo, ClientInfo, ClientSubscriptionResponse, CreateInstanceRequest, EditClientRequest, EditInstanceOptions, GeneralInfo, Instance, InstanceSubscriptionResponse, InstanceTemplate, ListInstancesOptions, RoleInfo, Service, TokenResponse, UserInfo } from './types/system';
import { WebSocketClient } from './WebSocketClient';


export default class YamcsClient implements HttpHandler {

  readonly apiUrl: string;
  readonly authUrl: string;
  readonly staticUrl: string;

  private accessToken?: string;

  private interceptor: HttpInterceptor;

  public connected$: Observable<boolean>;
  private webSocketClient?: WebSocketClient;

  constructor(readonly baseHref = '/') {
    this.apiUrl = `${this.baseHref}api`;
    this.authUrl = `${this.baseHref}auth`;
    this.staticUrl = `${this.baseHref}static`;
  }

  createInstanceClient(instance: string) {
    return new InstanceClient(instance, this);
  }

  async getInstanceUpdates(): Promise<InstanceSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getInstanceUpdates();
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

  async getInstances(options: ListInstancesOptions = {}) {
    const url = `${this.apiUrl}/instances`;
    const response = await this.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as InstancesWrapper;
    return wrapper.instance || [];
  }

  async getInstanceTemplates() {
    const response = await this.doFetch(`${this.apiUrl}/instance-templates`);
    const wrapper = await response.json() as InstanceTemplatesWrapper;
    return wrapper.template || [];
  }

  async getInstanceTemplate(name: string) {
    const response = await this.doFetch(`${this.apiUrl}/instance-templates/${name}`);
    return await response.json() as InstanceTemplate;
  }

  async getInstance(name: string) {
    const response = await this.doFetch(`${this.apiUrl}/instances/${name}`);
    return await response.json() as Instance;
  }

  async editInstance(name: string, options: EditInstanceOptions) {
    const body = JSON.stringify(options);
    return this.doFetch(`${this.apiUrl}/instances/${name}`, {
      body,
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

  async getUsers() {
    const url = `${this.apiUrl}/users`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as UsersWrapper;
    return wrapper.users || [];
  }

  async getUser(username: string) {
    const url = `${this.apiUrl}/users/${username}`;
    const response = await this.doFetch(url);
    return await response.json() as UserInfo;
  }

  async getRoles() {
    const url = `${this.apiUrl}/roles`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as RolesWrapper;
    return wrapper.roles || [];
  }

  async getRole(name: string) {
    const url = `${this.apiUrl}/roles/${name}`;
    const response = await this.doFetch(url);
    return await response.json() as RoleInfo;
  }

  async getClients() {
    const url = `${this.apiUrl}/clients`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as ClientsWrapper;
    return wrapper.client || [];
  }

  async getClient(id: number) {
    const url = `${this.apiUrl}/clients/${id}`;
    const response = await this.doFetch(url);
    return await response.json() as ClientInfo;
  }

  async getClientUpdates(): Promise<ClientSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getClientUpdates();
  }

  async getRocksDbDatabases() {
    const url = `${this.apiUrl}/archive/rocksdb/databases`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as RocksDbDatabasesWrapper;
    return wrapper.database || [];
  }

  async getRocksDbDatabaseProperties(tablespace: string, dbPath='') {
    const url = `${this.apiUrl}/archive/rocksdb/${tablespace}/properties/${dbPath}`;
    const response = await this.doFetch(url);
    return await response.text();
  }

  async compactRocksDbDatabase(tablespace: string, dbPath='') {
    const url = `${this.apiUrl}/archive/rocksdb/${tablespace}/compact/${dbPath}`;
    return await this.doFetch(url);
  }

  async editClient(clientId: number, options: EditClientRequest) {
    const body = JSON.stringify(options);
    const url = `${this.apiUrl}/clients/${clientId}`;
    return await this.doFetch(url, {
      body,
      method: 'PATCH',
    });
  }

  async createInstance(options: CreateInstanceRequest) {
    const body = JSON.stringify(options);
    const response = await this.doFetch(`${this.apiUrl}/instances`, {
      body,
      method: 'POST',
    })
    return await response.json() as Instance;
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
      this.webSocketClient = new WebSocketClient(this.baseHref);
      this.connected$ = this.webSocketClient.connected$;
    }
  }

  closeConnection() {
    if (this.webSocketClient) {
      this.webSocketClient.close();
      this.webSocketClient = undefined;
    }
  }

  private queryString(options: {[key: string]: any}) {
    const qs = Object.keys(options)
      .map(k => `${k}=${options[k]}`)
      .join('&');
    return qs === '' ? qs : '?' + qs;
  }
}
