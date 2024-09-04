import { styleTags, tags as t } from "@lezer/highlight";

export const filterHighlighting = styleTags({
  String: t.string,
  Text: t.literal,
  LineComment: t.lineComment,
  CompareOp: t.compareOperator,
  Comparable: t.propertyName,
  LogicOp: t.logicOperator,
  Number: t.number,
  "True False": t.bool,
  Minus: t.operator,
  Null: t.null,
  "( )": t.paren
})
