package com.galyaminsky.speakenot.data.remote

import com.galyaminsky.speakenot.domain.Meaning
import com.galyaminsky.speakenot.domain.MeaningSummary
import com.galyaminsky.speakenot.domain.Word

fun WordDto.toDomain(): Word? {
    val validId = id?.takeIf { it > 0 } ?: return null
    val validText = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return Word(validId, validText, meanings.orEmpty().mapNotNull { it.toDomain() })
}

fun MeaningSummaryDto.toDomain(): MeaningSummary? =
    id?.takeIf { it > 0 }?.let { MeaningSummary(it, translation?.text?.trim()?.ifEmpty { null }, transcription?.trim()?.ifEmpty { null }, partOfSpeechCode?.trim()?.ifEmpty { null }) }

fun MeaningDto.toDomain(): Meaning? {
    val validId = id?.takeIf { it > 0 } ?: return null
    return Meaning(
        validId,
        text?.trim().orEmpty(),
        translation?.text?.trim()?.ifEmpty { null },
        transcription?.trim()?.ifEmpty { null },
        definition?.text?.trim()?.ifEmpty { null },
        examples.orEmpty().mapNotNull { it.text?.trim()?.takeIf(String::isNotEmpty) },
        partOfSpeechCode?.trim()?.ifEmpty { null },
        imageUrl?.trim()?.takeIf(String::isNotEmpty) ?: images?.let { imageElement ->
            runCatching {
                imageElement.asJsonArray.firstOrNull()?.let { first ->
                    if (first.isJsonPrimitive) first.asString
                    else first.asJsonObject.get("url")?.asString ?: first.asJsonObject.get("imageUrl")?.asString
                }
            }.getOrNull()
        }?.trim()?.ifEmpty { null },
        soundUrl?.trim()?.ifEmpty { null },
    )
}
