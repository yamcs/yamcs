import { WebSocketCall } from '../WebSocketCall';
import { NamedObjectId } from './mdb';
import { ParameterValue } from './monitoring';
import { Service, ServiceState } from './system';

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

export interface SubscribeProcessorsRequest {
  instance?: string;
  processor?: string;
}

export interface Processor {
  instance: string;
  name: string;
  type: string;
  creator: string;
  hasAlarms: boolean;
  hasCommanding: boolean;
  state: ServiceState;
  persistent: boolean;
  time: string;
  replay: boolean;
  replayRequest?: ReplayRequest;
  services: Service[];
}

export interface ReplayRequest {
  utcStart: string;
  utcStop: string;
  speed: ReplaySpeed;
}

export interface ReplaySpeed {
  type: 'AFAP' | 'FIXED_DELAY' | 'REALTIME';
  param: number;
}

export type TMStatisticsSubscription = WebSocketCall<SubscribeTMStatisticsRequest, Statistics>;

export type ParameterSubscription = WebSocketCall<SubscribeParametersRequest, SubscribeParametersData>;

export type ProcessorSubscription = WebSocketCall<SubscribeProcessorsRequest, Processor>;
