export class HttpError extends Error {

  readonly statusCode: number;
  readonly statusText: string;
  readonly detail?: ErrorDetail;

  constructor(readonly response: Response, serverMessage?: string, detail?: ErrorDetail) {
    super(serverMessage || response.statusText);
    this.statusCode = response.status;
    this.statusText = response.statusText;
    this.detail = detail;
  }
}

export interface ErrorDetail {
  [key: string]: any,
  "@type": string,
}
