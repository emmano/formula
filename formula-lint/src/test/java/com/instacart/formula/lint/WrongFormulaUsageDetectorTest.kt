package com.instacart.formula.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.detector.api.Scope
import com.instacart.formula.lint.WrongFormulaUsageDetector.Companion.issues
import org.junit.Test

class WrongFormulaUsageDetectorTest {
    private val FORMULA_CONTEXT_STUB = """
        package com.instacart.formula

        class FormulaContext<State> {
            open fun callback(crossinline transition: Transition.Factory.() -> Transition<State>): () -> Unit
            open fun callback(key: Any, crossinline transition: Transition.Factory.() -> Transition<State>): () -> Unit 
            open fun <UIEvent> eventCallback(crossinline transition: Transition.Factory.(UIEvent) -> Transition<State>): (UIEvent) -> Unit 
            open fun <UIEvent> eventCallback(key: Any, crossinline transition: Transition.Factory.(UIEvent) -> Transition<State>): (UIEvent) -> Unit
            open fun <ChildOutput> child(child: IFormula<Unit, ChildOutput>): ChildOutput
            open fun <ChildInput, ChildOutput> child(formula: IFormula<ChildInput, ChildOutput>, input: ChildInput): ChildOutput
            open fun updates(init: UpdateBuilder<State>.() -> Unit): List<Update<*>>
            open fun <Value> key(key: Any, create: () -> Value): Value

            class UpdateBuilder<State>() {
                open fun <Message> events(stream: Stream<Message>, crossinline transition: Transition.Factory.(Message) -> Transition<State>)
                open fun <Message> onEvent(stream: Stream<Message>, avoidParameterClash: Any = this, crossinline transition: Transition.Factory.(Message) -> Transition<State>)
            }
        }
    """.trimIndent()
    private val STATELESS_FORMULA_STUB = """
        package com.instacart.formula

        class StatelessFormula<Input, Output> {
            open fun evaluate(input: Input, context: FormulaContext<Unit>): Evaluation<Output>
            open fun key(input: Input): Any? = null
        }
    """.trimIndent()




    @Test
    fun callingFormulaContextOutsideOfEvaluate() {
        val kotlinExample = """
            |package com.instacart.formula
            |
            |class ExampleFormula : StatelessFormula<Unit, Unit>() {
            |   override fun evaluate(input: Unit, context: FormulaContext<Unit>): Evaluation<Unit> {
            |        return Evaluation(
            |            output = Unit,
            |            updates = context.updates {
            |                Stream.onInit().onEvent {
            |                    val callback = context.callback {
            |                        transition {}
            |                    }   
            |                    none()
            |                }
            |            }
            |        )
            |    }
            |}""".trimMargin()

        lint()
            .files(
                kotlin(FORMULA_CONTEXT_STUB),
                kotlin(STATELESS_FORMULA_STUB),
                kotlin(kotlinExample)
            )
//            .customScope(Scope.ALL_CLASSES_AND_LIBRARIES)
            .issues(*issues)
            .run()
            .expect("")
    }

    @Test
    fun callingFormulaContextOutsideOfEvaluateInDelegatedCall() {
        val delegatedCall = """
            package com.instacart.formula

            class DelegatedCall {

                fun illegalBehavior(context: FormulaContext<*>) {
                    context.callback { none() }
                }
            }
        """.trimIndent()

        val kotlinExample = """
            |package com.instacart.formula
            |
            |class ExampleFormula : StatelessFormula<Unit, Unit>() {
            |   override fun evaluate(input: Unit, context: FormulaContext<Unit>): Evaluation<Unit> {
            |        return Evaluation(
            |            output = Unit,
            |            updates = context.updates {
            |                Stream.onInit().onEvent {
            |                    DelegatedCall().illegalBehavior(context)   
            |                    none()
            |                }
            |            }
            |        )
            |    }
            |}""".trimMargin()

        lint()
            .files(
                kotlin(FORMULA_CONTEXT_STUB),
                kotlin(STATELESS_FORMULA_STUB),
                kotlin(delegatedCall),
                kotlin(kotlinExample)
            )
            .issues(*issues)
            .run()
            .expect("")
    }

        @Test
    fun usingAndroidLogWithTwoArguments() {
        lint()
            .files(
                java(
                    """
                |package foo;
                |import android.util.Log;
                |public class Example {
                |  public void log() {
                |    Log.d("TAG", "msg");
                |  }
                |}""".trimMargin()
                ),
                kotlin(
                    """
                |package foo
                |import android.util.Log
                |class Example {
                |  fun log() {
                |    Log.d("TAG", "msg")
                |  }
                |}""".trimMargin()
                )
            )
            .issues(*issues)
            .run()
            .expect(
                """
            |src/foo/Example.java:5: Warning: Using 'Log' instead of 'Timber' [LogNotTimber]
            |    Log.d("TAG", "msg");
            |    ~~~~~~~~~~~~~~~~~~~
            |src/foo/Example.kt:5: Warning: Using 'Log' instead of 'Timber' [LogNotTimber]
            |    Log.d("TAG", "msg")
            |    ~~~~~~~~~~~~~~~~~~~
            |0 errors, 2 warnings""".trimMargin()
            )
            .expectFixDiffs(
                """
            |Fix for src/foo/Example.java line 5: Replace with Timber.tag("TAG").d("msg"):
            |@@ -5 +5
            |-     Log.d("TAG", "msg");
            |+     Timber.tag("TAG").d("msg");
            |Fix for src/foo/Example.java line 5: Replace with Timber.d("msg"):
            |@@ -5 +5
            |-     Log.d("TAG", "msg");
            |+     Timber.d("msg");
            |Fix for src/foo/Example.kt line 5: Replace with Timber.tag("TAG").d("msg"):
            |@@ -5 +5
            |-     Log.d("TAG", "msg")
            |+     Timber.tag("TAG").d("msg")
            |Fix for src/foo/Example.kt line 5: Replace with Timber.d("msg"):
            |@@ -5 +5
            |-     Log.d("TAG", "msg")
            |+     Timber.d("msg")
            |""".trimMargin()
            )
    }
}