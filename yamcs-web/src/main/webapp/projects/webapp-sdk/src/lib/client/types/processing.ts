import { WebSocketCall } from '../WebSocketCall';
import { AlgorithmStatus, Container, NamedObjectId, Parameter } from './mdb';
import { ParameterValue, Value } from './monitoring';
import { Service, ServiceState } from './system';

export interface SubscribeTMStatisticsRequest {
  instance: string;
  processor: string;
}

export interface SubscribeAlgorithmStatusRequest {
  instance: string;
  processor: string;
  name: string;
}

export interface Statistics {
  instance: string;
  processor: string;
  tmstats: TmStatistics[];
  lastUpdated: string;
}

export interface ExtractPacketResponse {
  packetName: string;
  parameterValues: ExtractedParameter[];
  messages?: string[];
}

export interface ExtractedParameter {
  parameter: Parameter;
  entryContainer: Container;
  location: number;
  size: number;
  rawValue: Value;
  engValue: Value;
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

export interface PacketNamesResponse {
  packets: string[];
  links: string[];
}

export interface SubscribeParametersRequest {
  instance: string;
  processor: string;
  id: NamedObjectId[];
  abortOnInvalid: boolean;
  updateOnExpiration: boolean;
  sendFromCache: boolean;
  maxBytes?: number;
  action: 'REPLACE' | 'ADD' | 'REMOVE';
}

export interface SubscribeParametersData {
  mapping: { [key: number]: NamedObjectId; };
  info: { [key: number]: SubscribedParameterInfo; };
  invalid: NamedObjectId[];
  values: ParameterValue[];
}

export interface SubscribedParameterInfo {
  parameter: string;
  units?: string;
}

export interface SubscribeProcessorsRequest {
  instance?: string;
  processor?: string;
}

export interface SubscribeBackfillingRequest {
  instance: string;
}

export interface SubscribeBackfillingData {
  finished?: BackfillFinished[];
}

export interface BackfillFinished {
  start: string;
  stop: string;
  processedParameters: number;
}

export interface Processor {
  instance: string;
  name: string;
  type: string;
  creator: string;
  hasAlarms: boolean;
  hasCommanding: boolean;
  checkCommandClearance: boolean;
  state: ServiceState;
  persistent: boolean;
  protected: boolean;
  time: string;
  replay: boolean;
  replayRequest?: ReplayRequest;
  replayState?: string;
  services: Service[];
  acknowledgments: AcknowledgmentInfo[];
}

export interface AcknowledgmentInfo {
  name: string;
  description?: string;
}

export interface ReplayRequest {
  start: string;
  stop: string;
  speed: ReplaySpeed;
  endAction: string;
}

export interface ReplaySpeed {
  type: 'AFAP' | 'FIXED_DELAY' | 'REALTIME';
  param: number;
}

export interface DownloadCommandsOptions {
  /**
   * Inclusive lower bound
   */
  start?: string;
  /**
   * Exclusive upper bound
   */
  stop?: string;

  delimiter?: 'COMMA' | 'SEMICOLON' | 'TAB';
}

export type TMStatisticsSubscription = WebSocketCall<SubscribeTMStatisticsRequest, Statistics>;

export type AlgorithmStatusSubscription = WebSocketCall<SubscribeAlgorithmStatusRequest, AlgorithmStatus>;

export type ParameterSubscription = WebSocketCall<SubscribeParametersRequest, SubscribeParametersData>;

export type ProcessorSubscription = WebSocketCall<SubscribeProcessorsRequest, Processor>;

export type BackfillingSubscription = WebSocketCall<SubscribeBackfillingRequest, SubscribeBackfillingData>;
