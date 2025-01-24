package jetbrains.buildServer.depcache

import com.google.gson.Gson
import jetbrains.buildServer.agent.cache.depcache.invalidation.Serializable
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

data class DotnetDepCacheInvalidationData(private val absoluteCachesPathToFilePathToChecksum: Map<String, Map<String, String>>) : Serializable {

    override fun serialize(): ByteArray {
        return GSON.toJson(this).toByteArray(JSON_CHARSET)
    }

    companion object {
        private val JSON_CHARSET: Charset = StandardCharsets.UTF_8
        private val GSON = Gson()

        fun deserialize(bytes: ByteArray): DotnetDepCacheInvalidationData {
            return GSON.fromJson(String(bytes, JSON_CHARSET), DotnetDepCacheInvalidationData::class.java)
        }
    }
}