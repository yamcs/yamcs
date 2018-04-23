/**
 * Returns a resolved promise when the http request may continue,
 * or a rejected promise when the http request may not continue.
 */
export type HttpInterceptor = (url: string, init?: RequestInit) => Promise<void>;
