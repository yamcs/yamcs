import { WebSocketCall } from '../WebSocketCall';

export interface SubscribeEventsRequest {
  instance: string;
  filter?: string;
}

export type EventSeverity =
  'INFO' | 'WARNING' | 'ERROR' |
  'WATCH' | 'DISTRESS' | 'CRITICAL' | 'SEVERE'
  ;

export interface Event {
  source: string;
  generationTime: string;
  receptionTime: string;
  seqNumber: number;
  type: string;
  message: string;
  severity: EventSeverity;
  extra?: { [key: string]: string; };
}

export interface CreateEventRequest {
  message: string;
  type?: string;
  severity?: EventSeverity;
  time?: string;
  extra?: { [key: string]: string; };
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
   * Filter query
   */
  filter?: string;
  severity?: EventSeverity;
  source?: string | string[];
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
   * Filter query
   */
  filter?: string;
  severity?: EventSeverity;
  source?: string | string[];
  delimiter?: 'COMMA' | 'SEMICOLON' | 'TAB';
}

export type EventSubscription = WebSocketCall<SubscribeEventsRequest, Event>;
