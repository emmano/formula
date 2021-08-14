package com.instacart.formula

class DelegatedCall {

    fun illegalBehavior(context: FormulaContext<*>) {
        context.callback { none() }
    }
}