import java.io.File

fun strValue(value: Any?): String {
    return when(value) {
        null -> "()"
        is Double -> {
            if(value % 1 == 0.0) {
                value.toInt()
            } else {
                value
            }
        }
        is Callable -> "<function ${value.javaClass.name}>"
        is List<*> -> {
            "[" + value.joinToString(", ") { strValue(it) } + "]"
        }
        else -> value
    }.toString()
}
fun printValue(value: Any?) {
    print(strValue(value))
}
fun repl() {
    val interpreter = Interpreter("")
    while(true) {
        print("zy => ")
        val code = readlnOrNull() ?: break
        try {
            interpreter.hadError = false
            val tokenizer = Tokenizer(code)
            val parser = Parser(tokenizer)
            val ast = parser.expr()
            interpreter.source = code
            val result = interpreter.interpret(ast)
            print("\u001B[90m")
            print("=> ")
            printValue(result)
            print("\u001B[0m\n")
        } catch (e: Parser.ParseError) {
            continue
        } catch (e: Interpreter.InterpreterError) {
            continue
        } catch (e: Exception) {
            continue
        }
    }
}
fun main(args: Array<String>) {
    if(args.isEmpty()) {
        repl()
    } else {
        val code = File(args[0]).readText()
        val tokenizer = Tokenizer(code)
        val parser = Parser(tokenizer)
        val ast = parser.expr()
        val interpreter = Interpreter(code)
        interpreter.globals.define("args", args.drop(1).toList() as List<Any?>)
        interpreter.interpret(ast)
    }
}