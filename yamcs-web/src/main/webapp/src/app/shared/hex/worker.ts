/// <reference lib="webworker" />

import { BitRange } from '../BitRange';
import { HexModel } from './model';

interface InitOptions {
  base64String: string;
}

interface SelectOptions {
  bitpos: number;
  bitlength: number;
}

interface HighlightOptions {
  bitpos: number;
  bitlength: number;
}

let model: HexModel;

addEventListener('message', ({ data }) => {
  switch (data.type) {
    case 'init':
      model = handleInit(data.options);
      postMessage({
        type: 'model',
        model: model,
        html: model.printHTML(),
      });
      break;
    case 'select':
      const selection = handleSelect(data.options);
      postMessage({
        type: 'selection',
        selection,
      });
      break;
    case 'highlight':
      const ids = handleHighlight(data.options);
      postMessage({
        type: 'highlight',
        ids,
      });
      break;
    default:
      throw Error(`Unexpected worker request type ${data.type}`);
  }
});


/**
 * Builds the initial model, assigning ids to all positionable
 * elements, and stores model state for reuse in later actions.
 */
function handleInit(options: InitOptions): HexModel {
  const raw = atob(options.base64String);
  return new HexModel(raw);
}


function handleSelect(options: SelectOptions) {
  const range = new BitRange(options.bitpos, options.bitlength);
  return getElementIdsForRange(range);
}


function handleHighlight(options: HighlightOptions) {
  const range = new BitRange(options.bitpos, options.bitlength);
  return getElementIdsForRange(range);
}


function getElementIdsForRange(range: BitRange) {
  // Limit to max bitlength
  range = range.intersect(new BitRange(0, model.bitlength))!;

  const ids: string[] = [];
  for (const line of model.lines) {

    // Short-circuit for performance
    if (!range.overlaps(line.range)) {
      if (ids.length) {
        break;
      } else {
        continue;
      }
    }

    for (const component of line.hexComponents) {
      if (component.type === 'word') {
        for (const nibble of component.nibbles) {
          if (range.overlaps(nibble.range)) {
            ids.push(nibble.id);
          }
        }
      } else if (component.type === 'filler') {
        if (!component.trailing && range.containsBitExclusive(component.bitpos)) {
          ids.push(component.id);
        }
      }
    }
    for (const component of line.asciiComponents) {
      if (component.type === 'word') {
        for (const c of component.chars) {
          if (range.overlaps(c.range)) {
            ids.push(c.id);
          }
        }
      }
    }
  }

  return ids;
}
