import { WebSocketCall } from '../WebSocketCall';
import { Spec } from './config';
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
  links: Link[];
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
  actions?: ActionInfo[];
  extra?: { [key: string]: any; };
  parameters?: string[];
}

export interface ActionInfo {
  id: string;
  label: string;
  style: 'CHECK_BOX' | 'PUSH_BUTTON';
  enabled: boolean;
  checked: boolean;
  spec?: Spec;
}

export interface SubscribeLinksRequest {
  instance: string;
}

export type LinkStatus = 'OK' | 'UNAVAIL' | 'DISABLED' | 'FAILED';

export type InstancesSubscription = WebSocketCall<{}, Instance>;
export type LinkSubscription = WebSocketCall<SubscribeLinksRequest, LinkEvent>;
