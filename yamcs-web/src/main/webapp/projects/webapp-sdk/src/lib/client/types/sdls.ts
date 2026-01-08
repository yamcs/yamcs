export interface SdlsLinkConfig {
  spis: number[];
}

export interface SdlsSeqCtr {
  seq: string;
}

export interface SdlsSa {
  seq: string;
  algorithm: string;
  sdlsHeaderSize: number;
  sdlsTrailerSize: number;
  sdlsOverhead: number;
  keyLen: number;
}
