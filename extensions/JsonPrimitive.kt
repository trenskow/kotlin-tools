package trenskow.extensions

import kotlinx.serialization.json.*

private val INT_REGEX = Regex("""^-?\d+$""")
private val DOUBLE_REGEX = Regex("""^-?\d+\.\d+(?:E-?\d+)?$""")

val JsonObject.value: Map<String, Any?>
	get() {
		return this.toMap()
			.mapValues { it.value.value }
	}

val JsonArray.value: List<Any?>
	get() {
		return this.map { it.value }
	}

val JsonPrimitive.value: Any?
	get() {
		return when {
			this.isString -> this.content
			else -> this.content.toBooleanStrictOrNull()
				?: when {
					INT_REGEX.matches(this.content) -> this.int
					DOUBLE_REGEX.matches(this.content) -> this.double
					else -> throw IllegalArgumentException("Unknown type for JSON value: ${this.content}")
				}
		}
	}

val JsonElement.value: Any?
	get() {
		return when (this) {
			is JsonObject -> this.value
			is JsonArray -> this.value
			is JsonPrimitive -> this.value
			else -> null
		}
	}

val Any?.jsonElement: JsonElement?
	get() = when (this) {
		null -> JsonNull
		is String -> JsonPrimitive(this)
		is Int -> JsonPrimitive(this)
		is Float -> JsonPrimitive(this)
		is Double -> JsonPrimitive(this)
		is Boolean -> JsonPrimitive(this)
		is List<*> -> this.jsonElement
		is Map<*, *> -> this.jsonElement
		else -> null
	}

val List<*>.jsonElement: JsonArray
	get() = JsonArray(this.mapNotNull { it.jsonElement })

val Map<*, *>.jsonElement: JsonObject
	get() = JsonObject(this.mapNotNull { item ->
		(item.key as? String)?.let { key ->
			item.value.jsonElement?.let { Pair(key, it) }
		}
	}.toMap())

fun JsonElement.merge(other: JsonElement, encapsulatedPrimitives: Boolean = false): JsonElement {

	val self = this

	if (self is JsonArray && other is JsonArray) {
		return JsonArray(self + other)
	}

	if (self is JsonObject && other is JsonObject) {

		val selfUniqueKeys = self.filter { !other.keys.contains(it.key) }
		val otherUniqueKeys = other.filter { !self.keys.contains(it.key) }

		val commonKeys = other
			.filter { self.keys.contains(it.key) }
			.mapValues {
				it.value.merge(other[it.key] ?: throw Exception("Could not merge map."))
			}

		return JsonObject(selfUniqueKeys + commonKeys + otherUniqueKeys)

	}

	if (encapsulatedPrimitives) {
		return JsonArray(listOf(self, other))
	}

	return other

}
