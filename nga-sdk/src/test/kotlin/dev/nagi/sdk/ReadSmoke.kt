package dev.nagi.sdk

import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    check(System.getenv("NAGI_READ_SMOKE") == "1") { "Set NAGI_READ_SMOKE=1 to contact NGA (read only)" }
    val client = createNgaClient()
    if (System.getenv("NAGI_SEARCH_SMOKE") == "1") {
        val user = checkNotNull(client.findUser("血牙酱"))
        check(client.getUser(user.id).id == user.id)
        check(client.findUser("nagi_search_probe_no_such_user_928173") == null)
        println("Real NGA user UID/exact-name/missing-user lookup verified (no profile data logged)")
        try {
            val topics = client.searchTopics("android")
            println("Global topics parsed: ${topics.items.size}; success verified for page 1 only")
        } catch (_: NgaException.Restricted) {
            println("Global topic success UNVERIFIED: guest search restricted; restriction parsing verified")
        }
        return@runBlocking
    }
    try {
        val forums = client.getForums()
        println("Directory parsed: ${forums.size} forums")
        val forum = forums.firstOrNull { it.id == "310" && it.collectionId == null }
            ?: forums.firstOrNull { it.collectionId == null && !it.id.startsWith("-") }
            ?: forums.first { it.collectionId == null }
        val topics = client.getTopics(forum.id)
        println("Topics parsed: ${topics.items.size}")
        topics.items.firstOrNull()?.let {
            println("Posts parsed: ${client.getPosts(it.id).items.size}")
        }
    } catch (error: NgaException) {
        println("Real NGA API NOT verified: ${error.message}")
        throw error
    }
}
