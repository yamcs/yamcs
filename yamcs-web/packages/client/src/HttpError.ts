export class HttpError extends Error {

  constructor(readonly statusCode: number, ...args: any[]) {
    super(...args);
  }
}
