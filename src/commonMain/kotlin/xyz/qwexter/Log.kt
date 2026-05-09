package xyz.qwexter

object Log {
    fun info(msg: String, vararg kv: Pair<String, Any?>) = emit("INFO", msg, *kv)
    fun warn(msg: String, vararg kv: Pair<String, Any?>) = emit("WARN", msg, *kv)
    fun error(msg: String, vararg kv: Pair<String, Any?>) = emit("ERROR", msg, *kv)

    private fun emit(level: String, msg: String, vararg kv: Pair<String, Any?>) {
        val fields = kv.joinToString(",") { (k, v) -> "\"$k\":${jsonValue(v)}" }
        val extra = if (fields.isNotEmpty()) ",$fields" else ""
        println("""{"level":"$level","msg":${jsonValue(msg)}$extra}""")
    }

    private fun jsonValue(v: Any?): String = when (v) {
        null -> "null"
        is Number, is Boolean -> v.toString()
        else -> "\"${v.toString().replace("\\", "\\\\").replace("\"", "\\\"")}\""
    }
}
