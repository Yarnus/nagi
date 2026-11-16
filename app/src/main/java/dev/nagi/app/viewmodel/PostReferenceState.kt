package dev.nagi.app.viewmodel

import dev.nagi.sdk.Post
import dev.nagi.sdk.PostReference

val PostReference.key: String get() = "$topicId:$postId"

data class PostReferenceState(
    val post: Post? = null,
    val loading: Boolean = false,
    val error: String? = null,
)
