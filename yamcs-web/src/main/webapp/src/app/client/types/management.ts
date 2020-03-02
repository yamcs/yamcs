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

export type InstancesSubscription = WebSocketCall<{}, Instance>;
