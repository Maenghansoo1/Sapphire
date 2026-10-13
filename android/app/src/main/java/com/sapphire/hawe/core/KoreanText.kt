package com.sapphire.hawe.core

/** 마지막 글자에 받침이 있는지(한글이 아니면 없다고 봐요). */
fun hasFinalConsonant(word: String): Boolean {
    val last = word.lastOrNull() ?: return false
    return last in '가'..'힣' && (last - '가') % 28 != 0
}

/** 받침에 맞는 조사: `josa("달", "이라고", "라고")` → "이라고". */
fun josa(word: String, withConsonant: String, withoutConsonant: String): String =
    if (hasFinalConsonant(word)) withConsonant else withoutConsonant
