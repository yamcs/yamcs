import { WebSocketCall } from '../WebSocketCall';

export interface SubscribeEventsRequest {
  instance: string;
}

export type EventSeverity =
  'INFO' | 'WARNING' | 'ERROR' |
  'WATCH' | 'DISTRESS' | 'CRITICAL' | 'SEVERE'
  ;

export interface Event {
  source: string;
  generationTimeUTC: string;
  receptionTimeUTC: string;
  seqNumber: number;
  type: string;
  message: string;
  severity: EventSeverity;
}

export interface CreateEventRequest {
  message: string;
  type?: string;
  severity?: EventSeverity;
  time?: string;
}

export interface GetEventsOptions {
  /**
   * Inclusive lower bound
   */
  start?: string;
  /**
   * Exclusive upper bound
   */
  stop?: string;
  /**
   * Search string
   */
  q?: string;
  severity?: EventSeverity;
  source?: string | string[];
  pos?: number;
  limit?: number;
  order?: 'asc' | 'desc';
}

export interface DownloadEventsOptions {
  /**
   * Inclusive lower bound
   */
  start?: string;
  /**
   * Exclusive upper bound
   */
  stop?: string;
  /**
   * Search string
   */
  q?: string;
  severity?: EventSeverity;
  source?: string | string[];
}

export type EventSubscription = WebSocketCall<SubscribeEventsRequest, Event>;
