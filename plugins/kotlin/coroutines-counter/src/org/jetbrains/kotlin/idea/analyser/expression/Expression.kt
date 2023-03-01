// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.analyser.expression

import com.intellij.ui.scale.JBUIScale
import com.intellij.util.IconUtil
import org.jetbrains.kotlin.idea.analyser.expression.Expression.TerminatedExpression.*
import org.jetbrains.kotlin.psi.KtFunction
import javax.swing.Icon
import javax.swing.JLabel

sealed class Expression {
    sealed class TerminatedExpression : Expression() {
        companion object {
            fun createIcon(text: String): Icon =
                IconUtil.textToIcon(text, JLabel(), JBUIScale.scale(10.0f))
        }

        object SomeCoroutines : TerminatedExpression() {
            override val icon by lazy {
                createIcon("S")
            }

            override fun toString() = "Some coroutines could be launched"
        }

        object Infinity : TerminatedExpression() {
            override val icon by lazy {
                createIcon("I")
            }

            override fun toString() = "Infinity"
        }

        data class Constant(val num: Int) : TerminatedExpression() {
            companion object {
                val ZERO = Constant(0)
                val ONE = Constant(1)
            }

            override val icon by lazy {
                createIcon(this.toString())
            }

            override fun toString(): String = num.toString()
        }

        abstract val icon: Icon
    }

    class Fun(val ktFunction: KtFunction) : Expression() {

        override fun toString() = "Fun(${ktFunction.name})"
    }

    data class Bin(val left: Expression, val op: Operation, val right: Expression) : Expression() {
        override fun toString(): String = "($left $op $right)"
    }

    enum class Operation {
        PLUS {
            override fun toString() = "+"
        },
        TIMES {
            override fun toString() = "*"
        };
    }

    operator fun plus(expr: Expression): Expression =
        when {
            this is SomeCoroutines || expr is SomeCoroutines -> SomeCoroutines
            this is Infinity || expr is Infinity -> Infinity
            this is Constant && expr is Constant -> Constant(this.num + expr.num)
            else -> Bin(this, Operation.PLUS, expr)
        }
}