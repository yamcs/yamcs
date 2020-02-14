export interface Upload {
  filename: string;
  promise: Promise<Response>;

  complete?: boolean;
  err?: string;
}
