package com.door43.translationstudio.core.manifest

import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.modules.SerializersModule
import org.bibletranslationtools.resourcecontainer.IntAsStringSerializer

internal val manifestJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    encodeDefaults = true
    explicitNulls = false

    serializersModule = SerializersModule {
        contextual(String::class, IntAsStringSerializer)
    }
}

object DraftSerializer : JsonTransformingSerializer<Manifest.Draft?>(
    Manifest.Draft.serializer().nullable
) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element is JsonNull || element !is JsonObject || element.isEmpty()) return JsonNull
        return element
    }

    override fun transformSerialize(element: JsonElement): JsonElement {
        return element
    }
}