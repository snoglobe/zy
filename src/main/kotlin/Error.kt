class Error(private val pos: Pair<Int, Int>, private val msg: String, private val src: String, val fatal: Boolean = false) {
    fun print() {
        val lines = src.split("\n")
        val line = lines[pos.first - 1]
        if(fatal) {
            // red color
            println("\u001B[31mfatal error ${pos}: $msg\u001B[0m")
        } else {
            // yellow color
            println("\u001B[33merror ${pos}: $msg\u001B[0m")
        }
        println(line)
        println(" ".repeat(pos.second - 1) + "^")
    }
}