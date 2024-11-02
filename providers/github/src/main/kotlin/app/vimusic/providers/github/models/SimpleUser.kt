package app.vimusic.providers.github.models

import app.vimusic.providers.utils.SerializableUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SimpleUser(
    val name: String? = null,
    val email: String? = null,
    val login: String,
    val id: Int,
    @SerialName("node_id")
    val nodeId: String,
    @SerialName("avatar_url")
    val avatarUrl: SerializableUrl,
    @SerialName("gravatar_id")
    val gravatarId: String? = null,
    val url: SerializableUrl,
    @SerialName("html_url")
    val frontendUrl: SerializableUrl,
    @SerialName("followers_url")
    val followersUrl: SerializableUrl,
    @SerialName("following_url")
    val followingUrl: SerializableUrl,
    @SerialName("gists_url")
    val gistsUrl: SerializableUrl,
    @SerialName("starred_url")
    val starredUrl: SerializableUrl,
    @SerialName("subscriptions_url")
    val subscriptionsUrl: SerializableUrl,
    @SerialName("organizations_url")
    val organizationsUrl: SerializableUrl,
    @SerialName("repos_url")
    val reposUrl: SerializableUrl,
    @SerialName("events_url")
    val eventsUrl: SerializableUrl,
    @SerialName("received_events_url")
    val receivedEventsUrl: SerializableUrl,
    val type: String,
    @SerialName("site_admin")
    val admin: Boolean
)
