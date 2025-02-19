package jetbrains.buildServer.depcache

import com.google.gson.Gson
import jetbrains.buildServer.agent.cache.depcache.invalidation.Serializable
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

data class DotnetDepCacheProjectFilesChecksum(private val absoluteCachesPathToChecksum: Map<String, String>) : Serializable {

    override fun serialize(): ByteArray {
        return GSON.toJson(this).toByteArray(JSON_CHARSET)
    }

    companion object {
        private val JSON_CHARSET: Charset = StandardCharsets.UTF_8
        private val GSON = Gson()

        fun deserialize(bytes: ByteArray): DotnetDepCacheProjectFilesChecksum {
            return GSON.fromJson(String(bytes, JSON_CHARSET), DotnetDepCacheProjectFilesChecksum::class.java)
        }
    }
}