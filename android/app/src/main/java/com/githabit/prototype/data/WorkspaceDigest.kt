package com.githabit.prototype.data

/** Lowercase, locale-independent encoding of the small SHA-256 result, not the document. */
internal fun ByteArray.hexDigest(): String {
    val digits = "0123456789abcdef"
    return CharArray(size * 2).also { result ->
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 255
            result[index * 2] = digits[value shr 4]
            result[index * 2 + 1] = digits[value and 15]
        }
    }.concatToString()
}
