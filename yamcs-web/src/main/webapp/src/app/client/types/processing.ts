import { WebSocketCall } from '../WebSocketCall';
import { NamedObjectId } from './mdb';
import { ParameterValue } from './monitoring';

export interface SubscribeTMStatisticsRequest {
  instance: string;
  processor: string;
}

export interface Statistics {
  instance: string;
  processor: string;
  tmstats: TmStatistics[];
  lastUpdated: string;
}

export interface TmStatistics {
  packetName: string;
  receivedPackets: number;
  packetRate: number;
  dataRate: number;
  lastReceived: string;
  lastPacketTime: string;
  subscribedParameterCount: number;
}

export interface SubscribeParametersRequest {
  instance: string;
  processor: string;
  id: NamedObjectId[];
  abortOnInvalid: boolean;
  updateOnExpiration: boolean;
  sendFromCache: boolean;
  action: 'REPLACE' | 'ADD' | 'REMOVE';
}

export interface SubscribeParametersData {
  mapping: { [key: number]: NamedObjectId; };
  invalid: NamedObjectId[];
  values: ParameterValue[];
}

export type TMStatisticsSubscription = WebSocketCall<SubscribeTMStatisticsRequest, Statistics>;

export type ParameterSubscription = WebSocketCall<SubscribeParametersRequest, SubscribeParametersData>;
