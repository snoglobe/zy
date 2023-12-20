import Token.*
import kotlin.system.exitProcess

class Parser(private val tokenizer: Tokenizer) {
    class ParseError(private val pos: Pair<Int, Int>, private val msg: String) : Throwable() {
        fun toError(p: Parser): Error {
            return Error(pos, msg, p.tokenizer.input, true)
        }
    }
    private var current: Token = tokenizer.next()!!
    private inline fun <reified T> peek(): Boolean {
        return current is T
    }
    private fun peek(type: KeywordType): Boolean {
        return peek<Keyword>() && (current as Keyword).type == type
    }
    private fun peek(type: OperatorType): Boolean {
        return peek<Operator>() && (current as Operator).type == type
    }
    private fun eat(): Token {
        val token = current
        current = tokenizer.next()!!
        return token
    }
    private inline fun <reified T> eat(): T {
        val token = current
        if(!peek<T>()) {
            throw ParseError(current.pos, "Expected ${T::class.simpleName} but got ${token::class.simpleName}")
        }
        current = tokenizer.next() ?: throw ParseError(current.pos, "Unexpected end of input")
        return token as T
    }
    private fun eat(type: KeywordType): Token {
        val token = current
        if (!peek<Keyword>() || (current as Keyword).type != type) {
            throw ParseError(current.pos, "Expected '${type.name.lowercase()}' but got '${token::class.simpleName}'")
        }
        if ((token as Keyword).type != type) {
            throw ParseError(current.pos, "Expected '${type.name.lowercase()}' but got '${type.name.lowercase()}'")
        }
        current = tokenizer.next()!!
        return token
    }
    private fun eat(type: OperatorType): Token {
        val token = current
        if (!peek<Operator>() || (current as Operator).type != type) {
            throw ParseError(current.pos, "Expected '${type.name.lowercase()}' but got '${token::class.simpleName}'")
        }
        if ((token as Operator).type != type) {
            throw ParseError(current.pos, "Expected '${type.name.lowercase()}' but got '${type.name}'")
        }
        current = tokenizer.next()!!
        return token
    }
    fun expr(): Node {
        try {
            var left = compose()
            while(peek(OperatorType.SEMICOLON)) {
                eat(OperatorType.SEMICOLON)
                left = Node.Seq(left, expr(), left.pos)
            }
            return left
        } catch(e: ParseError) {
            e.toError(this).print()
            throw e
        }
    }
    private fun compose(): Node {
        var left = or()
        while(peek(OperatorType.COMPOSE) || peek(OperatorType.CONCAT)) {
            val op = eat() as Operator
            left = Node.BinOp(op, left, or(), op.pos)
        }
        return left
    }
    private fun or(): Node {
        var left = and()
        while(peek(OperatorType.OR)) {
            left = Node.BinOp(eat(OperatorType.OR) as Operator, left, and(), left.pos)
        }
        return left
    }
    private fun and(): Node {
        var left = eq()
        while(peek(OperatorType.AND)) {
            left = Node.BinOp(eat(OperatorType.AND) as Operator, left, eq(), left.pos)
        }
        return left
    }
    private fun eq(): Node {
        var left = cmp()
        while(peek(OperatorType.EQUALS) || peek(OperatorType.NOT_EQUALS)) {
            left = Node.BinOp(eat() as Operator, left, cmp(), left.pos)
        }
        return left
    }
    private fun cmp(): Node {
        var left = add()
        while (peek(OperatorType.GREATER_THAN) || peek(OperatorType.LESS_THAN)
                || peek(OperatorType.GREATER_THAN_OR_EQUAL) || peek(OperatorType.LESS_THAN_OR_EQUAL)) {
            left = Node.BinOp(eat() as Operator, left, cmp(), left.pos)
        }
        return left
    }
    private fun add(): Node {
        var left = mul()
        while (peek(OperatorType.PLUS) || peek(OperatorType.MINUS)) {
            left = Node.BinOp(eat() as Operator, left, add(), left.pos)
        }
        return left
    }
    private fun mul(): Node {
        var left = unary()
        while (peek(OperatorType.TIMES) || peek(OperatorType.DIVIDE)) {
            left = Node.BinOp(eat() as Operator, left, mul(), left.pos)
        }
        return left
    }
    private fun unary(): Node {
        return if (peek(OperatorType.NOT)) {
            Node.UnOp(eat(OperatorType.NOT) as Operator, unary(), current.pos)
        } else {
            assign()
        }
    }
    private fun assign(): Node {
        val left = call()
        if(peek<DoubleColon>()) {
            eat<DoubleColon>()
            val value = expr()
            eat(KeywordType.IN)
            val body = expr()
            return Node.Assign(left, value, body, left.pos)
        }
        return left
    }
    private fun call(): Node {
        var left = atom()
        while(peek<LParen>()) {
            val args = args()
            left = Node.Call(left, args, left.pos)
        }
        return left
    }
    private fun args(): List<Node> {
        val args = mutableListOf<Node>()
        eat<LParen>()
        if (!peek<RParen>()) {
            args.add(expr())
            while (peek<Comma>()) {
                eat<Comma>()
                args.add(expr())
            }
        }
        eat<RParen>()
        return args
    }
    private fun next(): Node? {
        return if(peek(KeywordType.AND)) {
            eat(KeywordType.AND)
            if(!peek(KeywordType.LET) && !peek(KeywordType.FN)) {
                throw ParseError(current.pos, "Expected let or fn but got ${current::class.simpleName}")
            }
            atom()
        } else {
            null
        }
    }
    private fun atom(): Node {
        return when {
            peek(KeywordType.LET) -> {
                eat(KeywordType.LET)
                val name = eat<Identifier>().name
                eat<DoubleColon>()
                val value = expr()
                if(peek(KeywordType.AND)) {
                    next()!!
                } else {
                    val body = if(peek(KeywordType.IN)) {
                        eat(KeywordType.IN)
                        expr()
                    } else {
                        Node.Nil(value.pos)
                    }
                    val next = next()
                    Node.Let(name, value, body, next, value.pos)
                }
            }
            peek(KeywordType.FN) -> {
                val pos = eat(KeywordType.FN).pos
                val name = if (peek<Identifier>()) eat<Identifier>().name else null
                val args = mutableListOf<String>()
                if(peek<LParen>()) {
                    eat<LParen>()
                    if(!peek<RParen>()) {
                        args.add(eat<Identifier>().name)
                        while(peek<Comma>()) {
                            eat<Comma>()
                            args.add(eat<Identifier>().name)
                        }
                    }
                    eat<RParen>()
                }
                eat<DoubleColon>()
                val body = expr()
                val next = next()
                if (peek(KeywordType.END)) eat(KeywordType.END)
                Node.Fn(name, args, body, next, pos)
            }
            peek(KeywordType.IF) -> {
                val pos = eat(KeywordType.IF).pos
                val cond = expr()
                eat(KeywordType.THEN)
                val then = expr()
                eat(KeywordType.ELSE)
                val els = expr()
                Node.If(cond, then, els, pos)
            }
            peek<BooleanLiteral>() -> {
                val token = eat<BooleanLiteral>()
                Node.Bool(token.value, token.pos)
            }
            peek<Identifier>() -> {
                val token = eat<Identifier>()
                Node.Var(token.name, token.pos)
            }
            peek<Number>() -> {
                val token = eat<Token.Number>()
                Node.Num(token.value, token.pos)
            }
            peek<String>() -> {
                val token = eat<StringLiteral>()
                Node.Str(token.value, token.pos)
            }
            peek<LParen>() -> {
                val pos = eat<LParen>().pos
                if(peek<RParen>()) {
                    eat<RParen>()
                    Node.Nil(pos)
                } else {
                    val node = expr()
                    eat<RParen>()
                    node
                }
            }
            peek<Token.Number>() -> {
                val token = eat<Token.Number>()
                Node.Num(token.value, token.pos)
            }
            peek<StringLiteral>() -> {
                val token = eat<StringLiteral>()
                Node.Str(token.value, token.pos)
            }
            peek<LBracket>() -> {
                val pos = eat<LBracket>().pos
                val items = mutableListOf<Node>()
                if(!peek<RBracket>()) {
                    items.add(expr())
                    while(peek<Comma>()) {
                        eat<Comma>()
                        items.add(expr())
                    }
                }
                eat<RBracket>()
                Node.ListLit(items, pos)
            }
            else -> {
                throw ParseError(current.pos, "Expected an expression but got ${current::class.simpleName}")
            }
        }
    }
}