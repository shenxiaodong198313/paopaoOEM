package com.paopaoime.aikeyboard.ime.knowledge.store

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.VectorDistanceType

@Entity
data class KnowledgeChunk(
    @Id var id: Long = 0,
    var docId: String = "",
    var text: String = "",
    // Dimensions: 512 for bge-small-zh-v1.5. Change if using different model.
    @HnswIndex(dimensions = 512, distanceType = VectorDistanceType.COSINE)
    var vector: FloatArray? = null,
    var metadata: String = "{}"
)

