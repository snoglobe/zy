import kotlin.system.exitProcess
import Error as Error1

sealed class Token(open val pos: Pair<Int, Int>) {
    enum class KeywordType {
        FN, LET, IN, IF, THEN, ELSE, AND, END
    }
    data class Keyword(val type: KeywordType, override val pos: Pair<Int, Int>) : Token(pos)
    data class Identifier(val name: String, override val pos: Pair<Int, Int>) : Token(pos)
    data class Number(val value: Double, override val pos: Pair<Int, Int>) : Token(pos)
    data class StringLiteral(val value: String, override val pos: Pair<Int, Int>) : Token(pos)
    data class BooleanLiteral(val value: Boolean, override val pos: Pair<Int, Int>) : Token(pos)
    enum class OperatorType {
        PLUS, MINUS, TIMES, DIVIDE, EQUALS, NOT_EQUALS,
        LESS_THAN, GREATER_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN_OR_EQUAL,
        AND, OR, NOT, COMPOSE, SEMICOLON, CONCAT
    }
    data class Operator(val type: OperatorType, override val pos: Pair<Int, Int>) : Token(pos)
    data class LParen(override val pos: Pair<Int, Int>) : Token(pos)
    data class RParen(override val pos: Pair<Int, Int>) : Token(pos)
    data class LBracket(override val pos: Pair<Int, Int>) : Token(pos)
    data class RBracket(override val pos: Pair<Int, Int>) : Token(pos)
    data class DoubleColon(override val pos: Pair<Int, Int>) : Token(pos)
    data class Comma(override val pos: Pair<Int, Int>) : Token(pos)
    data class EOF(override val pos: Pair<Int, Int>) : Token(pos)
}
class Tokenizer(val input: String) {
    private var pos = 0
    private var line = 1
    private var col = 1
    private fun skip(m: MatchResult): Token? {
        m.hashCode() // to suppress warning
        return null
    }
    private val regexes = mutableMapOf(
        Regex("([a-zA-Z_][a-zA-Z0-9_]*)") to { m: MatchResult ->
            when (m.groupValues[1]) {
                "fn" -> Token.KeywordType.FN
                "let" -> Token.KeywordType.LET
                "in" -> Token.KeywordType.IN
                "if" -> Token.KeywordType.IF
                "then" -> Token.KeywordType.THEN
                "else" -> Token.KeywordType.ELSE
                "and" -> Token.KeywordType.AND
                "end" -> Token.KeywordType.END
                else -> null
            }.let {
                if (it != null) Token.Keyword(it, Pair(line, col))
                else Token.Identifier(m.groupValues[1], Pair(line, col))
            }
        },
        Regex("([0-9]+(\\.[0-9]+)?)") to { m: MatchResult -> Token.Number(m.groupValues[1].toDouble(), Pair(line, col)) },
        Regex("\"([^\"]*)\"") to { m: MatchResult -> Token.StringLiteral(m.groupValues[1], Pair(line, col)) },
        Regex("true") to { _: MatchResult -> Token.BooleanLiteral(true, Pair(line, col)) },
        Regex("false") to { _: MatchResult -> Token.BooleanLiteral(false, Pair(line, col)) },
        Regex("\\+") to { _: MatchResult -> Token.Operator(Token.OperatorType.PLUS, Pair(line, col)) },
        Regex("-") to { _: MatchResult -> Token.Operator(Token.OperatorType.MINUS, Pair(line, col)) },
        Regex("\\*") to { _: MatchResult -> Token.Operator(Token.OperatorType.TIMES, Pair(line, col)) },
        Regex("/") to { _: MatchResult -> Token.Operator(Token.OperatorType.DIVIDE, Pair(line, col)) },
        Regex("=") to { _: MatchResult -> Token.Operator(Token.OperatorType.EQUALS, Pair(line, col)) },
        Regex("!=") to { _: MatchResult -> Token.Operator(Token.OperatorType.NOT_EQUALS, Pair(line, col)) },
        Regex("<") to { _: MatchResult -> Token.Operator(Token.OperatorType.LESS_THAN, Pair(line, col)) },
        Regex(">") to { _: MatchResult -> Token.Operator(Token.OperatorType.GREATER_THAN, Pair(line, col)) },
        Regex("<=") to { _: MatchResult -> Token.Operator(Token.OperatorType.LESS_THAN_OR_EQUAL, Pair(line, col)) },
        Regex(">=") to { _: MatchResult -> Token.Operator(Token.OperatorType.GREATER_THAN_OR_EQUAL, Pair(line, col)) },
        Regex("&&") to { _: MatchResult -> Token.Operator(Token.OperatorType.AND, Pair(line, col)) },
        Regex("\\|\\|") to { _: MatchResult -> Token.Operator(Token.OperatorType.OR, Pair(line, col)) },
        Regex("!") to { _: MatchResult -> Token.Operator(Token.OperatorType.NOT, Pair(line, col)) },
        Regex("\\.\\.") to { _: MatchResult -> Token.Operator(Token.OperatorType.CONCAT, Pair(line, col)) },
        Regex("\\.") to { _: MatchResult -> Token.Operator(Token.OperatorType.COMPOSE, Pair(line, col)) },
        Regex(";") to { _: MatchResult -> Token.Operator(Token.OperatorType.SEMICOLON, Pair(line, col)) },
        Regex("::") to { _: MatchResult -> Token.DoubleColon(Pair(line, col)) },
        Regex("\\(") to { _: MatchResult -> Token.LParen(Pair(line, col)) },
        Regex("\\)") to { _: MatchResult -> Token.RParen(Pair(line, col)) },
        Regex("\\[") to { _: MatchResult -> Token.LBracket(Pair(line, col)) },
        Regex("]") to { _: MatchResult -> Token.RBracket(Pair(line, col)) },
        Regex(",") to { _: MatchResult -> Token.Comma(Pair(line, col)) },
        Regex("\\s+") to ::skip,
        Regex("'.*") to ::skip,
    )
    fun next(): Token? {
        if (pos >= input.length) return Token.EOF(Pair(line, col))
        for ((regex, f) in regexes) {
            val m = regex.find(input, pos)
            if (m != null && m.range.first == pos) {
                val token = f(m)
                pos = m.range.last + 1
                col += m.range.last - m.range.first + 1
                line += m.groupValues[0]
                    .count { it == '\n' }
                    .also { if (it > 0)
                        col = m.groupValues[0].length - m.groupValues[0].lastIndexOf('\n')
                    }
                if(token == null) return next()
                return token
            }
        }
        Error1(Pair(line, col), "Unexpected character '${input[pos]}'", input, true).print()
        throw Exception()
    }
}