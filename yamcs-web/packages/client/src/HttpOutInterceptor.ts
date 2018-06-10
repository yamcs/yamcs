/**
 * Returns a resolved promise when the http request may continue,
 * or a rejected promise when the http request may not continue.
 */
export type HttpOutInterceptor = (url: string, response: Response) => Promise<void>;
