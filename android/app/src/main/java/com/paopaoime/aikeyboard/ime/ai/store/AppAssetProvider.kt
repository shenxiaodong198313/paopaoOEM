package com.paopaoime.aikeyboard.ime.ai.store

import com.paopaoime.aikeyboard.compat.agent.data.entity.AgentAsset
import com.paopaoime.aikeyboard.compat.agent.data.repository.AgentAssetProvider

class AppAssetProvider(private val localAssetManager: LocalAssetManager) : AgentAssetProvider {
    override suspend fun getAssets(offset: Int, limit: Int): List<AgentAsset> {
        val allAssets = localAssetManager.getAssets().sortedByDescending { it.createTime }
        
        // Simple pagination in memory (since getAssets returns all)
        if (offset >= allAssets.size) return emptyList()
        
        val end = (offset + limit).coerceAtMost(allAssets.size)
        return allAssets.subList(offset, end).map { local ->
            AgentAsset(
                id = local.id,
                type = local.type,
                path = local.localPath,
                coverPath = local.coverPath.takeIf { it.isNotEmpty() },
                width = local.width,
                height = local.height,
                prompt = local.prompt,
                createTime = local.createTime
            )
        }
    }
}

