import { ERROR_BG, SUCCESS_BG } from './colors';

const SKIP_FILL = '#ffffff';
const SKIP_STROKE = '#eeeeee';

function createStripePattern(stroke: string) {
  const offscreen = document.createElement('canvas');
  // Using 10x10 or 20x20 makes the math for 45-degree stripes easy
  offscreen.width = 12;
  offscreen.height = 12;
  const ctx = offscreen.getContext('2d')!;

  // 1. Background fill
  ctx.fillStyle = SKIP_FILL;
  ctx.fillRect(0, 0, offscreen.width, offscreen.height);

  // 2. Draw diagonal stripes
  // We draw the line twice (top-left and bottom-right corners)
  // so it tiles perfectly when repeated.
  ctx.beginPath();
  ctx.strokeStyle = stroke;
  ctx.lineWidth = 4; // Adjust thickness for more/less "grey"

  // Main diagonal
  ctx.moveTo(-offscreen.width / 2, offscreen.height / 2);
  ctx.lineTo(offscreen.width / 2, -offscreen.height / 2);

  // Offset diagonal for seamless tiling
  ctx.moveTo(0, offscreen.height);
  ctx.lineTo(offscreen.width, 0);

  // Third line for the other corner
  ctx.moveTo(offscreen.width / 2, offscreen.height * 1.5);
  ctx.lineTo(offscreen.width * 1.5, offscreen.height / 2);

  ctx.stroke();

  return ctx.createPattern(offscreen, 'repeat')!;
}

export const skippedPattern = createStripePattern(ERROR_BG);
export const plannedPattern = createStripePattern(SKIP_STROKE);
export const inProgressPattern = createStripePattern(SUCCESS_BG);
