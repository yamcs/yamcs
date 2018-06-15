export interface HttpHandler {

  handle(url: string, init?: RequestInit): Promise<Response>;
}
