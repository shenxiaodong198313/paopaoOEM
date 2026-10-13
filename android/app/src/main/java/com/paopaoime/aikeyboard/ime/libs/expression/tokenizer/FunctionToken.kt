package com.paopaoime.aikeyboard.ime.libs.expression.tokenizer

import com.paopaoime.aikeyboard.ime.libs.expression.function.Function

class FunctionToken(@JvmField val function: Function) : Token(TOKEN_FUNCTION)

