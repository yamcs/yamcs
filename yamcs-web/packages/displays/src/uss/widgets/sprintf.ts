/*

This file was adapted from https://github.com/alexei/sprintf.js
The original package did not support es6-style imports. We only
use this for one very specific case, so we may refactor out our
dependency on this file at some point.

Original license header:
------------------------

Copyright (c) 2007-present, Alexandru Mărășteanu <hello@alexei.ro>
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* Neither the name of this software nor the names of its contributors may be
  used to endorse or promote products derived from this software without
  specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

/* tslint:disable */

const re = {
  not_string: /[^s]/,
  not_bool: /[^t]/,
  not_type: /[^T]/,
  not_primitive: /[^v]/,
  number: /[diefg]/,
  numeric_arg: /[bcdiefguxX]/,
  json: /[j]/,
  not_json: /[^j]/,
  text: /^[^\x25]+/,
  modulo: /^\x25{2}/,
  placeholder: /^\x25(?:([1-9]\d*)\$|\(([^\)]+)\))?(\+)?(0|'[^$])?(-)?(\d+)?(?:\.(\d+))?([b-gijostTuvxX])/,
  key: /^([a-z_][a-z_\d]*)/i,
  key_access: /^\.([a-z_][a-z_\d]*)/i,
  index_access: /^\[(\d+)\]/,
  sign: /^[\+\-]/
};

export function sprintf(key: any, ...args: any[]) {
  // `arguments` is not an array, but should be fine for this call
  return sprintf_format(sprintf_parse(key), args);
}

function sprintf_format(parse_tree: any, argv: any) {
  var cursor = 1, tree_length = parse_tree.length, arg, output = '', i, k, match, pad, pad_character, pad_length, is_positive, sign
  for (i = 0; i < tree_length; i++) {
    if (typeof parse_tree[i] === 'string') {
      output += parse_tree[i];
    } else if (Array.isArray(parse_tree[i])) {
      match = parse_tree[i]; // convenience purposes only
      if (match[2]) { // keyword argument
        arg = argv[cursor];
        for (k = 0; k < match[2].length; k++) {
          if (!arg.hasOwnProperty(match[2][k])) {
            throw new Error(`[sprintf] property "${match[2][k]}" does not exist`);
          }
          arg = arg[match[2][k]];
        }
      }
      else if (match[1]) { // positional argument (explicit)
        arg = argv[match[1]];
      } else { // positional argument (implicit)
        arg = argv[cursor++]
      }

      if (re.not_type.test(match[8]) && re.not_primitive.test(match[8]) && arg instanceof Function) {
        arg = arg()
      }

      if (re.numeric_arg.test(match[8]) && (typeof arg !== 'number' && isNaN(arg))) {
        throw new TypeError(`[sprintf] expecting number but found ${arg}`);
      }

      if (re.number.test(match[8])) {
        is_positive = arg >= 0;
      }

      switch (match[8]) {
        case 'b':
          arg = parseInt(arg, 10).toString(2);
          break;
        case 'c':
          arg = String.fromCharCode(parseInt(arg, 10));
          break;
        case 'd':
        case 'i':
          arg = parseInt(arg, 10);
          break;
        case 'j':
          arg = JSON.stringify(arg, null, match[6] ? parseInt(match[6]) : 0);
          break;
        case 'e':
          arg = match[7] ? parseFloat(arg).toExponential(match[7]) : parseFloat(arg).toExponential();
          break;
        case 'f':
          arg = match[7] ? parseFloat(arg).toFixed(match[7]) : parseFloat(arg);
          break;
        case 'g':
          arg = match[7] ? String(Number(arg.toPrecision(match[7]))) : parseFloat(arg);
          break;
        case 'o':
          arg = (parseInt(arg, 10) >>> 0).toString(8);
          break;
        case 's':
          arg = String(arg);
          arg = (match[7] ? arg.substring(0, match[7]) : arg);
          break;
        case 't':
          arg = String(!!arg);
          arg = (match[7] ? arg.substring(0, match[7]) : arg);
          break;
        case 'T':
          arg = Object.prototype.toString.call(arg).slice(8, -1).toLowerCase();
          arg = (match[7] ? arg.substring(0, match[7]) : arg);
          break;
        case 'u':
          arg = parseInt(arg, 10) >>> 0;
          break;
        case 'v':
          arg = arg.valueOf();
          arg = (match[7] ? arg.substring(0, match[7]) : arg);
          break;
        case 'x':
          arg = (parseInt(arg, 10) >>> 0).toString(16);
          break;
        case 'X':
          arg = (parseInt(arg, 10) >>> 0).toString(16).toUpperCase();
          break;
      }
      if (re.json.test(match[8])) {
        output += arg;
      } else {
        if (re.number.test(match[8]) && (!is_positive || match[3])) {
          sign = is_positive ? '+' : '-';
          arg = arg.toString().replace(re.sign, '');
        } else {
          sign = '';
        }
        pad_character = match[4] ? match[4] === '0' ? '0' : match[4].charAt(1) : ' ';
        pad_length = match[6] - (sign + arg).length;
        pad = match[6] ? (pad_length > 0 ? pad_character.repeat(pad_length) : '') : '';
        output += match[5] ? sign + arg + pad : (pad_character === '0' ? sign + pad + arg : pad + sign + arg);
      }
    }
  }
  return output
}

const sprintf_cache = Object.create(null);

function sprintf_parse(fmt: any) {
  if (sprintf_cache[fmt]) {
    return sprintf_cache[fmt];
  }

  var _fmt = fmt, match, parse_tree: any[] = [], arg_names = 0
  while (_fmt) {
    if ((match = re.text.exec(_fmt)) !== null) {
      parse_tree.push(match[0]);
    } else if ((match = re.modulo.exec(_fmt)) !== null) {
      parse_tree.push('%');
    } else if ((match = re.placeholder.exec(_fmt)) !== null) {
      if (match[2]) {
        arg_names |= 1;
        var field_list: any = [], replacement_field = match[2], field_match: any = [];
        if ((field_match = re.key.exec(replacement_field)) !== null) {
          field_list.push(field_match[1]);
          while ((replacement_field = replacement_field.substring(field_match[0].length)) !== '') {
            if ((field_match = re.key_access.exec(replacement_field)) !== null) {
              field_list.push(field_match[1]);
            } else if ((field_match = re.index_access.exec(replacement_field)) !== null) {
              field_list.push(field_match[1]);
            } else {
              throw new SyntaxError('[sprintf] failed to parse named argument key');
            }
          }
        } else {
            throw new SyntaxError('[sprintf] failed to parse named argument key')
        }
        match[2] = field_list;
      } else {
        arg_names |= 2;
      }
      if (arg_names === 3) {
        throw new Error('[sprintf] mixing positional and named placeholders is not (yet) supported');
      }
      parse_tree.push(match);
    } else {
      throw new SyntaxError('[sprintf] unexpected placeholder')
    }
    _fmt = _fmt.substring(match[0].length);
  }
  return sprintf_cache[fmt] = parse_tree;
}
