import { WebSocketCall } from '../WebSocketCall';
import { Instance } from './system';

export interface ListInstancesOptions {
  filter?: string;
}

export interface CreateInstanceRequest {
  name: string;
  template: string;
  templateArgs?: { [key: string]: string; };
  labels?: { [key: string]: string; };
}

export interface LinkEvent {
  type: string;
  linkInfo: Link;
}

export interface Link {
  instance: string;
  name: string;
  type: string;
  spec: string;
  stream: string;
  disabled: boolean;
  dataInCount: number;
  dataOutCount: number;
  status: LinkStatus;
  detailedStatus: string;
  parentName?: string;
}

export interface EditLinkOptions {
  state?: 'enabled' | 'disabled';
  resetCounters?: boolean;
}

export interface SubscribeLinksRequest {
  instance: string;
}

export type LinkStatus = 'OK' | 'UNAVAIL' | 'DISABLED' | 'FAILED';

export type InstancesSubscription = WebSocketCall<{}, Instance>;
export type LinkSubscription = WebSocketCall<SubscribeLinksRequest, LinkEvent>;
