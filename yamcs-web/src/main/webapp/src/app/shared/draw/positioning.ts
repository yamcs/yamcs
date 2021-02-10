export interface Bounds {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface Point {
  x: number;
  y: number;
}

export function crispen(original: Bounds): Bounds {
  return {
    x: Math.round(original.x) + 0.5,
    y: Math.round(original.y) + 0.5,
    width: Math.round(original.width),
    height: Math.round(original.height),
  };
}
