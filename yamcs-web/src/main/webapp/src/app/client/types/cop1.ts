import { WebSocketCall } from '../WebSocketCall';

export type Cop1State = 'ACTIVE'
    | 'RETRANSMIT_WITHOUT_WAIT'
    | 'RETRANSMIT_WITH_WAIT'
    | 'INITIALIZING_WITHOUT_BC'
    | 'INITIALIZING_WITH_BC'
    | 'UNINTIALIZED'
    | 'SUSPENDED';

export interface Clcw {
    receptionTime: string;
    lockout: boolean;
    wait: boolean;
    retransmit: boolean;
    nR: number;
}

export interface Cop1Status {
    link: string;
    cop1Active: boolean;
    setBypassAll: boolean;
    clcw: Clcw;
    state: Cop1State;
    vS: number;
    nnR: number;
    waitQueueNumTC: number;
    sentQueueNumFrames: number;
    outQueueNumFrames: number;
    txCount: number;
}

export interface Cop1Config {
    link: string;
    vcId: number;
    bdAbsolutePriority: boolean;
    windowWidth: number;
    timeoutType: string;
    txLimit: number;
    t1: number;
}

export interface SubscribeCop1Request {
    instance: string;
    link: string;
}

export type Cop1Subscription = WebSocketCall<SubscribeCop1Request, Cop1Status>;
