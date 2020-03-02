import { Observable } from 'rxjs';
import { HttpError } from './HttpError';
import { HttpHandler } from './HttpHandler';
import { HttpInterceptor } from './HttpInterceptor';
import { InstanceClient } from './InstanceClient';
import { Cop1Status, Cop1Subscription, SubscribeCop1Request } from './types/cop1';
import { ClientConnectionsWrapper, GroupsWrapper, InstancesWrapper, InstanceTemplatesWrapper, RocksDbDatabasesWrapper, RolesWrapper, ServicesWrapper, UsersWrapper } from './types/internal';
import { CreateInstanceRequest, InstancesSubscription, ListInstancesOptions } from './types/management';
import { CreateProcessorRequest } from './types/monitoring';
import { AuthInfo, CreateGroupRequest, CreateServiceAccountRequest, CreateServiceAccountResponse, CreateUserRequest, EditClearanceRequest, EditClientRequest, EditGroupRequest, EditUserRequest, GeneralInfo, GroupInfo, Instance, InstanceTemplate, LeapSecondsTable, ListClearancesResponse, ListRoutesResponse, ListServiceAccountsResponse, RoleInfo, Service, ServiceAccount, SystemInfo, TokenResponse, UserInfo } from './types/system';
import { SubscribeTimeRequest, Time, TimeSubscription } from './types/time';
import { WebSocketClient2 } from './WebSocketClient2';


export default class YamcsClient implements HttpHandler {

  readonly apiUrl: string;
  readonly authUrl: string;
  readonly staticUrl: string;

  private accessToken?: string;

  private interceptor: HttpInterceptor;

  public connected$: Observable<boolean>;
  private webSocketClient?: WebSocketClient2;

  constructor(readonly baseHref = '/') {
    this.apiUrl = `${this.baseHref}api`;
    this.authUrl = `${this.baseHref}auth`;
    this.staticUrl = `${this.baseHref}static`;
  }

  createInstanceClient(instance: string) {
    return new InstanceClient(instance, this);
  }

  createInstancesSubscription(observer: (instance: Instance) => void): InstancesSubscription {
    this.prepareWebSocketClient();
    return this.webSocketClient!.createSubscription('instances', {}, observer);
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
    headers.append('Content-Type', 'application/x-www-form-urlencoded');
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

  async getRoutes() {
    const url = `${this.apiUrl}/routes`;
    const response = await this.doFetch(url);
    return await response.json() as ListRoutesResponse;
  }

  async getClearances() {
    const url = `${this.apiUrl}/clearances`;
    const response = await this.doFetch(url);
    return await response.json() as ListClearancesResponse;
  }

  async changeClearance(username: string, options: EditClearanceRequest) {
    const body = JSON.stringify(options);
    return this.doFetch(`${this.apiUrl}/clearances/${username}`, {
      method: 'PATCH',
      body,
    });
  }

  async deleteClearance(username: string) {
    return this.doFetch(`${this.apiUrl}/clearances/${username}`, {
      method: 'DELETE',
    });
  }

  async createProcessor(options: CreateProcessorRequest) {
    const body = JSON.stringify(options);
    return await this.doFetch(`${this.apiUrl}/processors`, {
      body,
      method: 'POST',
    });
  }

  async getLeapSeconds() {
    const url = `${this.apiUrl}/leap-seconds`;
    const response = await this.doFetch(url);
    return await response.json() as LeapSecondsTable;
  }

  async getInstances(options: ListInstancesOptions = {}) {
    const url = `${this.apiUrl}/instances`;
    const response = await this.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as InstancesWrapper;
    return wrapper.instances || [];
  }

  async getInstanceTemplates() {
    const response = await this.doFetch(`${this.apiUrl}/instance-templates`);
    const wrapper = await response.json() as InstanceTemplatesWrapper;
    return wrapper.templates || [];
  }

  async getInstanceTemplate(name: string) {
    const response = await this.doFetch(`${this.apiUrl}/instance-templates/${name}`);
    return await response.json() as InstanceTemplate;
  }

  async getInstance(name: string) {
    const response = await this.doFetch(`${this.apiUrl}/instances/${name}`);
    return await response.json() as Instance;
  }

  async startInstance(name: string) {
    return this.doFetch(`${this.apiUrl}/instances/${name}:start`, {
      method: 'POST',
    });
  }

  async stopInstance(name: string) {
    return this.doFetch(`${this.apiUrl}/instances/${name}:stop`, {
      method: 'POST',
    });
  }

  async restartInstance(name: string) {
    return this.doFetch(`${this.apiUrl}/instances/${name}:restart`, {
      method: 'POST',
    });
  }

  async getServices(): Promise<Service[]> {
    const response = await this.doFetch(`${this.apiUrl}/services/_global`);
    const wrapper = await response.json() as ServicesWrapper;
    return wrapper.services || [];
  }

  async getService(name: string): Promise<Service> {
    const url = `${this.apiUrl}/services/_global/${name}`;
    const response = await this.doFetch(url);
    return await response.json() as Service;
  }

  async startService(name: string) {
    return this.doFetch(`${this.apiUrl}/services/_global/${name}:start`, {
      method: 'POST',
    });
  }

  async stopService(name: string) {
    return this.doFetch(`${this.apiUrl}/services/_global/${name}:stop`, {
      method: 'POST',
    });
  }

  async getServiceAccounts() {
    const url = `${this.apiUrl}/service-accounts`;
    const response = await this.doFetch(url);
    return await response.json() as ListServiceAccountsResponse;
  }

  async getServiceAccount(name: string) {
    const url = `${this.apiUrl}/service-accounts/${name}`;
    const response = await this.doFetch(url);
    return await response.json() as ServiceAccount;
  }

  async createServiceAccount(options: CreateServiceAccountRequest) {
    const body = JSON.stringify(options);
    const url = `${this.apiUrl}/service-accounts`;
    const response = await this.doFetch(url, {
      body,
      method: 'POST',
    });
    return await response.json() as CreateServiceAccountResponse;
  }

  async deleteServiceAccount(name: string) {
    const url = `${this.apiUrl}/service-accounts/${name}`;
    return await this.doFetch(url, {
      method: 'DELETE',
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

  async createUser(options: CreateUserRequest) {
    const body = JSON.stringify(options);
    const url = `${this.apiUrl}/users`;
    return await this.doFetch(url, {
      body,
      method: 'POST',
    });
  }

  async editUser(username: string, options: EditUserRequest) {
    const body = JSON.stringify(options);
    const url = `${this.apiUrl}/users/${username}`;
    return await this.doFetch(url, {
      body,
      method: 'PATCH',
    });
  }

  async deleteIdentity(username: string, provider: string) {
    const url = `${this.apiUrl}/users/${username}/identities/${provider}`;
    const response = await this.doFetch(url, { method: 'DELETE' });
    return await response.json() as UserInfo;
  }

  async createGroup(options: CreateGroupRequest) {
    const body = JSON.stringify(options);
    const response = await this.doFetch(`${this.apiUrl}/groups`, {
      body,
      method: 'POST',
    });
    return await response.json() as GroupInfo;
  }

  async getGroups() {
    const url = `${this.apiUrl}/groups`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as GroupsWrapper;
    return wrapper.groups || [];
  }

  async getGroup(name: string) {
    const url = `${this.apiUrl}/groups/${name}`;
    const response = await this.doFetch(url);
    return await response.json() as GroupInfo;
  }

  async editGroup(name: string, options: EditGroupRequest) {
    const body = JSON.stringify(options);
    const url = `${this.apiUrl}/groups/${name}`;
    return await this.doFetch(url, {
      body,
      method: 'PATCH',
    });
  }

  async deleteGroup(name: string) {
    const url = `${this.apiUrl}/groups/${name}`;
    return await this.doFetch(url, { method: 'DELETE' });
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

  async getClientConnections() {
    const url = `${this.apiUrl}/connections`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as ClientConnectionsWrapper;
    return wrapper.connections || [];
  }

  async closeClientConnection(id: string) {
    const url = `${this.apiUrl}/connections/${id}`;
    return await this.doFetch(url, { method: 'DELETE' });
  }

  createTimeSubscription(options: SubscribeTimeRequest, observer: (time: Time) => void): TimeSubscription {
    this.prepareWebSocketClient();
    return this.webSocketClient!.createSubscription('time', options, observer);
  }

  createCop1Subscription(options: SubscribeCop1Request, observer: (cop1Status: Cop1Status) => void): Cop1Subscription {
    this.prepareWebSocketClient();
    return this.webSocketClient!.createSubscription('cop1', options, observer);
  }

  async getSystemInfo() {
    const url = `${this.apiUrl}/sysinfo`;
    const response = await this.doFetch(url);
    return await response.json() as SystemInfo;
  }

  async getRocksDbDatabases() {
    const url = `${this.apiUrl}/archive/rocksdb/databases`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as RocksDbDatabasesWrapper;
    return wrapper.databases || [];
  }

  async getRocksDbDatabaseProperties(tablespace: string, dbPath = '') {
    const url = `${this.apiUrl}/archive/rocksdb/${tablespace}/${dbPath}:describe`;
    const response = await this.doFetch(url);
    return await response.text();
  }

  async compactRocksDbDatabase(tablespace: string, dbPath = '') {
    const url = `${this.apiUrl}/archive/rocksdb/${tablespace}/${dbPath}:compact`;
    return await this.doFetch(url, {
      method: 'POST',
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

  async createInstance(options: CreateInstanceRequest) {
    const body = JSON.stringify(options);
    const response = await this.doFetch(`${this.apiUrl}/instances`, {
      body,
      method: 'POST',
    });
    return await response.json() as Instance;
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
    try {
      if (this.interceptor) {
        response = await this.interceptor(this, url, init);
      } else {
        response = await this.handle(url, init);
      }
    } catch (err) { // NOTE: Fetch fails with "TypeError" on network or CORS failures.
      return Promise.reject(err);
    }

    // Make non 2xx responses available to clients via 'catch' instead of 'then'.
    if (response.ok) {
      return Promise.resolve(response);
    } else {
      return new Promise<Response>((resolve, reject) => {
        response.json().then(json => {
          reject(new HttpError(response, json['msg']));
        }).catch(err => {
          console.error('Failure while handling server error', err);
          reject(new HttpError(response));
        });
      });
    }
  }

  handle(url: string, init?: RequestInit) {
    // Our handler uses Fetch API, available in modern browsers.
    // For older browsers, the end application should include an
    // appropriate polyfill.
    return fetch(url, init);
  }

  prepareWebSocketClient() {
    if (!this.webSocketClient) {
      this.webSocketClient = new WebSocketClient2(this.apiUrl);
      this.connected$ = this.webSocketClient.connected$;
    }
  }

  private queryString(options: { [key: string]: any; }) {
    const qs = Object.keys(options)
      .map(k => `${k}=${options[k]}`)
      .join('&');
    return qs === '' ? qs : '?' + qs;
  }
}
