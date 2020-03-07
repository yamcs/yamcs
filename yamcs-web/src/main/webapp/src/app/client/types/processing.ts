import { WebSocketCall } from '../WebSocketCall';

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

export type TMStatisticsSubscription = WebSocketCall<SubscribeTMStatisticsRequest, Statistics>;
