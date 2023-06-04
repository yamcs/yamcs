const indent = '&nbsp;&nbsp;&nbsp;&nbsp;';
const tokenExpression = /[()]|(, ")/;

/**
 * A very simplicistic syntax colorer for StreamSQL definitions.
 * Only patterns impacting indent are used for tokenizing the input.
 * Other style annotations are added with replace-all regexps.
 */
export function formatSQL(sql: string) {
  if (!sql) {
    return sql;
  }

  const parts: string[] = [];
  let totalIndent = '';

  let tokenIndex = sql.search(tokenExpression);
  while (tokenIndex !== -1) {
    if (sql[tokenIndex] === '(') {
      parts.push(sql.substring(0, tokenIndex + 1));
      totalIndent += indent;
      parts.push('<br>', totalIndent);

      sql = sql.substring(tokenIndex + 1);
      tokenIndex = sql.search(tokenExpression);
    } else if (sql[tokenIndex] === ')') {
      parts.push(sql.substring(0, tokenIndex));
      totalIndent = totalIndent.replace(indent, '');
      parts.push('<br>', totalIndent);
      parts.push(')');

      sql = sql.substring(tokenIndex + 1);
      tokenIndex = sql.search(tokenExpression);
    } else { // Should be a column separation
      parts.push(sql.substring(0, tokenIndex + 1));
      parts.push('<br>', totalIndent);
      parts.push('"');

      sql = sql.substring(tokenIndex + 3);
      tokenIndex = sql.search(tokenExpression);
    }
  }

  let res = parts.join('') + sql;

  // Start options on a new line
  res = res.replace(/(engine|histogram|partition|primary|table_format)/g, '<br>$1');

  return stylize(res);
}

function stylize(text: string) {
  return text
    .replace(/(BINARY|ENUM|HRES_TIMESTAMP|INT|LONG|PARAMETER_VALUE|PROTOBUF|STRING|TIMESTAMP)/g, '<span class="dtype">$1</span>')
    .replace(/([(),]+)/g, '<span class="paren">$1</span>')
    .replace(/(auto_increment|create|compressed|histogram|engine|primary key|table(_format)?|stream)/g, '<span class="kw">$1</span>')
    .replace(/(partition by time(_and_value)?|partition by value)/g, '<span class="kw">$1</span>');
}
