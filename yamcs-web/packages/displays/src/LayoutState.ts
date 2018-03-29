export interface FrameState {
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface LayoutState {

  frames: FrameState[];
}
