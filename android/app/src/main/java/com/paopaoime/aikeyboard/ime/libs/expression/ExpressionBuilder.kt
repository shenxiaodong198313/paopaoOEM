package com.paopaoime.aikeyboard.ime.libs.expression

import com.paopaoime.aikeyboard.ime.libs.expression.function.Function
import com.paopaoime.aikeyboard.ime.libs.expression.function.Functions
import com.paopaoime.aikeyboard.ime.libs.expression.operator.Operator
import com.paopaoime.aikeyboard.ime.libs.expression.shuntingyard.ShuntingYard

class ExpressionBuilder(expression: String?) {
    private val expression: String
    private val userFunctions: Map<String, Function>
    private val userOperators: Map<String, Operator>
    private val variableNames: MutableSet<String>
    private val implicitMultiplication = true

    init {
        require(!(expression == null || expression.trim { it <= ' ' }.isEmpty())) { "Expression can not be empty" }
        this.expression = expression
        userOperators = HashMap(4)
        userFunctions = HashMap(4)
        variableNames = HashSet(4)
    }

    fun build(): Expression {
        require(expression.isNotEmpty()) { "The expression can not be empty" }
        variableNames.add("pi")
        variableNames.add("蟺")
        variableNames.add("e")
        variableNames.add("蠁")
        for (name in variableNames) {
            require(!(Functions.getBuiltinFunction(name) != null || userFunctions.containsKey(name))) { "A variable can not have the same name as a function [$name]" }
        }
        return Expression(ShuntingYard.convertToRPN(expression, userFunctions, userOperators, variableNames, implicitMultiplication))
    }
}

