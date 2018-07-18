export class HttpError extends Error {

  readonly statusCode: number;
  readonly statusText: string;

  constructor(readonly response: Response) {
    super(response.statusText);
    this.statusCode = response.status;
    this.statusText = response.statusText;
  }
}
