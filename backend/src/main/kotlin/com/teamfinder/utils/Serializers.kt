package com.teamfinder.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import com.teamfinder.models.Skill

class SkillSerializer : KSerializer<Skill> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Skill") {
        element<String>("name")
        element<String>("level")
        element<String>("category")
    }

    override fun serialize(encoder: Encoder, value: Skill) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeStringElement(descriptor, 0, value.name)
        value.level?.let { composite.encodeStringElement(descriptor, 1, it) }
        value.category?.let { composite.encodeStringElement(descriptor, 2, it) }
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): Skill {
        var name = ""
        var level: String? = null
        var category: String? = null

        val composite = decoder.beginStructure(descriptor)
        for (i in 0 until descriptor.elementsCount) {
            when (i) {
                0 -> name = composite.decodeStringElement(descriptor, i)
                1 -> level = composite.decodeNullableSerializableElement(descriptor, i, kotlinx.serialization.builtins.serializer<String?>())
                2 -> category = composite.decodeNullableSerializableElement(descriptor, i, kotlinx.serialization.builtins.serializer<String?>())
            }
        }
        composite.endStructure(descriptor)

        return Skill(name = name, level = level, category = category)
    }
}

class ListSerializer<T>(private val serializer: kotlinx.serialization.Serializer<T>) : KSerializer<List<T>> {
    override val descriptor: SerialDescriptor = kotlinx.serialization.descriptors.buildClassSerialDescriptor("List")

    override fun serialize(encoder: Encoder, value: List<T>) {
        encoder.encodeString(kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(serializer), value))
    }

    override fun deserialize(decoder: Decoder): List<T> {
        return kotlinx.serialization.json.Json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(serializer), decoder.decodeString())
    }
}
