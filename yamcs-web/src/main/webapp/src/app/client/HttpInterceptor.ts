import { HttpHandler } from './HttpHandler';

/**
 * Should pass url and init to the given HttpHandler.
 * Both the request and the response can be intercepted.
 */
export type HttpInterceptor = (next: HttpHandler, url: string, init?: RequestInit) => Promise<Response>;
