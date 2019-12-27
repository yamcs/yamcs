export interface OpenDisplayCommandOptions {
  target: string;
  openInNewWindow: boolean;
  coordinates?: {
    x: number;
    y: number;
    width: number;
    height: number;
  };
}
