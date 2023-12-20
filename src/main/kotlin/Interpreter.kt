import Callable.Companion.nativeFunction
import java.lang.NullPointerException
import kotlin.system.exitProcess

data class Environment(private val parent: Environment? = null) {
    private val values = mutableMapOf<String, Any?>()
    fun define(name: String, value: Any?) {

        values[name] = value
    }
    fun assign(name: String, value: Any?) {
        if(values.containsKey(name)) {
            values[name] = value
        } else {
            parent?.assign(name, value) ?: throw Exception("Undefined variable $name")
        }
    }
    fun get(name: String): Any? {
        return values[name] ?: parent?.get(name)
    }
}

interface Callable {
    fun call(interpreter: Interpreter, arg: Any?, env: Environment? = null): Any?
    companion object {
        fun nativeFunction(fn: (Interpreter, Any?, Environment?) -> Any?): Callable {
            return object : Callable {
                override fun call(interpreter: Interpreter, arg: Any?, env: Environment?): Any? {
                    return fn(interpreter, arg, env)
                }
            }
        }
    }
}

class Interpreter(var source: String) {
    val globals = Environment()
    var hadError = false
    class InterpreterError(private val pos: Pair<Int, Int>, private val msg: String, private val fatal: Boolean = true) : Throwable() {
        fun toError(interpreter: Interpreter): Error {
            return Error(pos, msg, interpreter.source, fatal)
        }
    }

    init {
        globals.define("print", nativeFunction { _, arg, _ ->
            printValue(arg)
            println()
            null
        })
        globals.define("read", nativeFunction { _, _, _ ->
            readlnOrNull()
        })
        globals.define("range", nativeFunction { _, from, _ ->
            nativeFunction { _, to, _  ->
                ((from as Number).toInt()..<(to as Number).toInt()).toList()
            }
        })
        globals.define("add", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                (arg as Number).toDouble() + (i as Number).toDouble()
            }
        })
        globals.define("sub", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                (arg as Number).toDouble() - (i as Number).toDouble()
            }
        })
        globals.define("mul", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                (arg as Number).toDouble() * (i as Number).toDouble()
            }
        })
        globals.define("div", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                (arg as Number).toDouble() / (i as Number).toDouble()
            }
        })
        globals.define("mod", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                (arg as Number).toDouble() % (i as Number).toDouble()
            }
        })
        globals.define("eq", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                arg == i
            }
        })
        globals.define("neq", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                arg != i
            }
        })
        globals.define("lt", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                (arg as Number).toDouble() < (i as Number).toDouble()
            }
        })
        globals.define("gt", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                (arg as Number).toDouble() > (i as Number).toDouble()
            }
        })
        globals.define("lte", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                (arg as Number).toDouble() <= (i as Number).toDouble()
            }
        })
        globals.define("gte", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                (arg as Number).toDouble() >= (i as Number).toDouble()
            }
        })
        globals.define("and", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                truthy(arg) && truthy(i)
            }
        })
        globals.define("or", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                truthy(arg) || truthy(i)
            }
        })
        globals.define("not", nativeFunction { _, arg, _ ->
            !truthy(arg)
        })
        globals.define("compose", nativeFunction { _, g, _ ->
            nativeFunction { _, x, _ ->
                (x as Callable).call(this, g)
            }
        })
        globals.define("toNum", nativeFunction { _, arg, _ ->
            (arg as String).toDouble()
        })
        globals.define("toStr", nativeFunction { _, arg, _ ->
            strValue(arg)
        })
        globals.define("toBool", nativeFunction { _, arg, _ ->
            truthy(arg)
        })
        globals.define("global", nativeFunction { interpreter, arg, _ ->
            interpreter.globals.get(arg as String)
        })
        globals.define("nth", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                (i as List<*>)[(arg as Number).toInt()]
            }
        })
        globals.define("head", nativeFunction { _, arg, _ ->
            (arg as List<*>).first()
        })
        globals.define("tail", nativeFunction { _, arg, _ ->
            (arg as List<*>).drop(1)
        })
        globals.define("len", nativeFunction { _, arg, _ ->
            (arg as List<*>).size
        })
        globals.define("map", nativeFunction { interpreter, arg, _ ->
            nativeFunction { _, fn, _ ->
                (arg as List<*>).map { (fn as Callable).call(interpreter, it) }
            }
        })
        globals.define("foldl", nativeFunction { interpreter, initial, _ ->
            nativeFunction { _, fn, _ ->
                nativeFunction { _, range, _ ->
                    (range as List<*>).fold(initial) { acc, i ->
                        ((fn as Callable).call(interpreter, acc) as Callable).call(interpreter, i)
                    }
                }
            }
        })
        globals.define("foldr", nativeFunction { interpreter, initial, _ ->
            nativeFunction { _, fn, _ ->
                nativeFunction { _, range, _ ->
                    (range as List<*>).foldRight(initial) { i, acc ->
                        ((fn as Callable).call(interpreter, i) as Callable).call(interpreter, acc)
                    }
                }
            }
        })
        globals.define("filter", nativeFunction { interpreter, arg, _ ->
            nativeFunction { _, fn, _ ->
                (arg as List<*>).filter { truthy((fn as Callable).call(interpreter, it)) }
            }
        })
        globals.define("reduce", nativeFunction { interpreter, arg, _ ->
            nativeFunction { _, fn, _ ->
                (arg as List<*>).reduce { acc, i ->
                    ((fn as Callable).call(interpreter, acc) as Callable).call(interpreter, i)
                }
            }
        })
        globals.define("zip", nativeFunction { interpreter, arg, _ ->
            nativeFunction { _, fn, _ ->
                (arg as List<*>).zip((fn as List<*>)).map { (it.first as Callable).call(interpreter, it.second) }
            }
        })
        globals.define("zipWith", nativeFunction { interpreter, arg, _ ->
            nativeFunction { _, fn, _ ->
                nativeFunction { _, arg2, _ ->
                    (arg as List<*>).zip((arg2 as List<*>)).map { ((fn as Callable).call(interpreter, it.first) as Callable).call(interpreter, it.second) }
                }
            }
        })
        globals.define("plus", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                (arg as List<*>).plus(i)
            }
        })
        globals.define("minus", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                (arg as List<*>).minus(i)
            }
        })
        globals.define("drop", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                (arg as List<*>).drop((i as Number).toInt())
            }
        })
        globals.define("take", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                (arg as List<*>).take((i as Number).toInt())
            }
        })
        globals.define("slice", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                nativeFunction { _, j, _ ->
                    (arg as List<*>).slice((i as Number).toInt()..(j as Number).toInt())
                }
            }
        })
        globals.define("concat", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                when(arg) {
                    is List<*> -> {
                        when(i) {
                            is List<*> -> arg + i
                            else -> arg + listOf(i)
                        }
                    }
                    else -> strValue(arg) + strValue(i)
                }
            }
        })
        globals.define("reverse", nativeFunction { _, arg, _ ->
            (arg as List<*>).reversed()
        })
        globals.define("sort", nativeFunction { _, arg, _ ->
            (arg as List<*>).sortedBy {
                when(it) {
                    is Number -> it.toDouble()
                    null -> throw RuntimeException("Cannot sort null")
                    else -> throw RuntimeException("Cannot sort type ${it::class.simpleName}")
                }
            }
        })
        globals.define("regex", nativeFunction { _, arg, _ ->
            Regex(arg as String)
        })
        globals.define("match", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                (arg as Regex).matches(i as String)
            }
        })
        globals.define("replace", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                nativeFunction { _, j, _ ->
                    (arg as Regex).replace(i as String, j as String)
                }
            }
        })
        globals.define("split", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                (arg as Regex).split(i as String)
            }
        })
        globals.define("join", nativeFunction { _, arg, _ ->
            nativeFunction { _, i, _ ->
                (arg as List<*>).joinToString(i as String)
            }
        })
    }
    private fun truthy(value: Any?): Boolean {
        return when(value) {
            null -> false
            is Boolean -> value
            else -> true
        }
    }

    private fun call(fn: Callable, args: List<Any?>): Any? {
        var fn2: Any? = fn
        return if(args.isEmpty()) {
            (fn2 as Callable).call(this, null)
        } else {
            for(element in args) {
                fn2 = (fn2 as Callable).call(this, element)
            }
            fn2
        }
    }

    fun interpret(node: Node, env: Environment = globals): Any? {
        try {
            return when (node) {
                is Node.Fn -> {
                    var result: Callable
                    if (node.args.isEmpty()) {
                        result = nativeFunction { _, _, _ ->
                            val closure = Environment(env)
                            interpret(node.body, closure)
                        }
                    } else {
                        result = nativeFunction { interpreter, arg, env2 ->
                            env2!!.define(node.args[0], arg)
                            interpreter.interpret(node.body, env2)
                        }
                        val args = node.args.subList(1, node.args.size).reversed()
                        for (argN in args) {
                            val prevResult = result
                            result = nativeFunction { _, arg, env2 ->
                                env2!!.define(argN, arg)
                                nativeFunction { interpreter, arg2, _ ->
                                    prevResult.call(interpreter, arg2, env2)
                                }
                            }
                        }
                        val prevResult = result
                        result = nativeFunction { interpreter, arg, _ ->
                            val closure = Environment(env)
                            prevResult.call(interpreter, arg, closure)
                        }
                    }
                    if (node.name != null) {
                        env.define(node.name, result)
                    }
                    if (node.next != null) {
                        interpret(node.next, env)
                    } else {
                        result
                    }
                }

                is Node.Let -> {
                    val value = interpret(node.value, env)
                    env.define(node.name, value)
                    val result = interpret(node.body, env)
                    if (node.next != null) {
                        interpret(node.next, env)
                    } else {
                        result
                    }
                }

                is Node.If -> {
                    val cond = interpret(node.cond, env)
                    if (cond == true) {
                        interpret(node.then, env)
                    } else {
                        interpret(node.els, env)
                    }
                }

                is Node.Call -> {
                    var fn = interpret(node.fn, env)
                    if(fn !is Callable) throw InterpreterError(node.pos, "Not a function")
                    if (node.args.isEmpty()) {
                        fn.call(this, null)
                    } else {
                        for (i in 0..<node.args.size) {
                            val arg = interpret(node.args[i], env)
                            if(fn !is Callable) throw InterpreterError(node.pos, "Extraneous argument in call")
                            fn = fn.call(this, arg)
                        }
                        fn
                    }
                }

                is Node.BinOp -> {
                    val left = interpret(node.left, env)
                    val right = interpret(node.right, env)
                    when (node.op.type) {
                        Token.OperatorType.PLUS -> call(env.get("add") as Callable, listOf(left, right))
                        Token.OperatorType.MINUS -> call(env.get("sub") as Callable, listOf(left, right))
                        Token.OperatorType.TIMES -> call(env.get("mul") as Callable, listOf(left, right))
                        Token.OperatorType.DIVIDE -> call(env.get("div") as Callable, listOf(left, right))
                        Token.OperatorType.EQUALS -> call(env.get("eq") as Callable, listOf(left, right))
                        Token.OperatorType.NOT_EQUALS -> call(env.get("neq") as Callable, listOf(left, right))
                        Token.OperatorType.LESS_THAN -> call(env.get("lt") as Callable, listOf(left, right))
                        Token.OperatorType.LESS_THAN_OR_EQUAL -> call(env.get("lte") as Callable, listOf(left, right))
                        Token.OperatorType.GREATER_THAN -> call(env.get("gt") as Callable, listOf(left, right))
                        Token.OperatorType.GREATER_THAN_OR_EQUAL -> call(
                            env.get("gte") as Callable,
                            listOf(left, right)
                        )
                        Token.OperatorType.AND -> call(env.get("and") as Callable, listOf(left, right))
                        Token.OperatorType.OR -> call(env.get("or") as Callable, listOf(left, right))
                        Token.OperatorType.COMPOSE -> call(env.get("compose") as Callable, listOf(left, right))
                        Token.OperatorType.CONCAT -> call(env.get("concat") as Callable, listOf(left, right))
                        else -> throw InterpreterError(node.pos, "Unknown operator ${node.op.type}")
                    }
                }

                is Node.UnOp -> {
                    val value = interpret(node.expr, env)
                    when (node.op.type) {
                        Token.OperatorType.MINUS -> (env.get("neg") as Callable).call(this, listOf(value))
                        Token.OperatorType.NOT -> (env.get("not") as Callable).call(this, listOf(value))
                        else -> throw InterpreterError(node.pos, "Unknown operator ${node.op.type}")
                    }
                }

                is Node.Num -> node.value
                is Node.Str -> node.value
                is Node.Bool -> node.value
                is Node.Var -> env.get(node.name) ?: throw InterpreterError(node.pos, "Undefined variable ${node.name}")
                is Node.Assign -> {
                    val value = interpret(node.right, env)
                    when (node.left) {
                        is Node.Var -> try {
                            env.assign(node.left.name, value)
                        } catch (e: Exception) {
                            throw InterpreterError(node.left.pos, e.message ?: "Unknown error")
                        }
                        else -> throw InterpreterError(node.left.pos, "Invalid assignment target")
                    }
                    interpret(node.next, env)
                }

                is Node.Seq -> {
                    interpret(node.left, env)
                    interpret(node.right, env)
                }

                is Node.ListLit -> {
                    val list = mutableListOf<Any?>()
                    for(element in node.value) {
                        list.add(interpret(element, env))
                    }
                    list
                }

                is Node.Nil -> null
            }
        } catch (e: InterpreterError) {
            val error = e.toError(this)
            if(!hadError) {
                error.print()
                hadError = true
            }
            if(error.fatal) {
                throw e
            }
            return null
        } catch(e: NullPointerException) {
            Error(node.pos, "Null value", source, true).print()
            throw e
        } catch (e: RuntimeException) {
                Error(node.pos, e.message ?: "Unknown error", source, true).print()
                throw e
        } catch (e: Exception) {
            Error(node.pos, e.message ?: "Unknown error", source, true).print()
            throw e
        } catch (e: StackOverflowError) {
            Error(node.pos, "Stack overflow", source, true).print()
            exitProcess(1)
        }
    }
}