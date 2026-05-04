package rylween.save

import rylween.dsl.GameModel
import rylween.runtime.RuntimeSnapshotMetadata

import java.lang.reflect.Array
import java.lang.reflect.Modifier
import java.security.MessageDigest
import java.util.IdentityHashMap

const val RuntimeSnapshotFormatVersion: Int = 1

fun GameModel.runtimeSnapshotMetadata(): RuntimeSnapshotMetadata =
    RuntimeSnapshotMetadata(
        formatVersion = RuntimeSnapshotFormatVersion,
        contentHash = runtimeContentHash(),
        universeId = metadata.universe.compatibilityId,
        universeVersion = metadata.universe.version,
    )

fun GameModel.runtimeContentHash(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val canonical = canonicalContentString(this)
    val hash = digest.digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
    return "sha256:$hash"
}

private fun canonicalContentString(value: Any?): String =
    buildString {
        appendCanonical(value, mutableIdentitySet())
    }

private fun mutableIdentitySet(): MutableSet<Any> =
    java.util.Collections.newSetFromMap(IdentityHashMap())

private fun StringBuilder.appendCanonical(value: Any?, visiting: MutableSet<Any>) {
    when (value) {
        null -> append("null")
        is String -> appendDslQuotedString(value)
        is Char -> appendDslQuotedString(value.toString())
        is Number,
        is Boolean -> append(value)
        is Enum<*> -> append(value.javaClass.name).append('.').append(value.name)
        is IntRange -> append("IntRange(").append(value.first).append("..").append(value.last).append(')')
        is Map<*, *> -> appendMap(value, visiting)
        is Set<*> -> appendSet(value, visiting)
        is Iterable<*> -> appendIterable(value, visiting)
        else -> appendObject(value, visiting)
    }
}

private fun StringBuilder.appendMap(value: Map<*, *>, visiting: MutableSet<Any>) {
    append("Map[")
    value.entries
        .map { entry -> canonicalContentString(entry.key, visiting) to canonicalContentString(entry.value, visiting) }
        .sortedBy { it.first }
        .forEach { (key, entryValue) ->
            append(key).append('=').append(entryValue).append(';')
        }
    append(']')
}

private fun StringBuilder.appendSet(value: Set<*>, visiting: MutableSet<Any>) {
    append("Set[")
    value.map { canonicalContentString(it, visiting) }.sorted().forEach { item ->
        append(item).append(';')
    }
    append(']')
}

private fun StringBuilder.appendIterable(value: Iterable<*>, visiting: MutableSet<Any>) {
    append("List[")
    value.forEach { item ->
        appendCanonical(item, visiting)
        append(';')
    }
    append(']')
}

private fun canonicalContentString(value: Any?, visiting: MutableSet<Any>): String =
    buildString {
        appendCanonical(value, visiting)
    }

private fun StringBuilder.appendObject(value: Any, visiting: MutableSet<Any>) {
    val type = value.javaClass
    if (type.isArray) {
        append("Array[")
        (0 until Array.getLength(value)).forEach { index ->
            appendCanonical(Array.get(value, index), visiting)
            append(';')
        }
        append(']')
        return
    }
    if (!visiting.add(value)) {
        append(type.name).append("<cycle>")
        return
    }

    try {
        append(type.name).append('{')
        type.declaredFields
            .filterNot { field -> field.isSynthetic || Modifier.isStatic(field.modifiers) }
            .sortedBy { it.name }
            .forEach { field ->
                check(field.trySetAccessible()) {
                    "cannot fingerprint ${type.name}.${field.name}"
                }
                append(field.name).append('=')
                appendCanonical(field.get(value), visiting)
                append(';')
            }
        append('}')
    } finally {
        visiting.remove(value)
    }
}
