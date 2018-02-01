import { DisplayFrame } from './DisplayFrame';

export interface LayoutListener {

  onDisplayFrameOpen(frame: DisplayFrame): void;

  onDisplayFrameClose(frame: DisplayFrame): void;
}
