package com.paopaoime.aikeyboard.compat.agent.data.repository

import com.paopaoime.aikeyboard.compat.agent.data.entity.AgentAsset

interface AgentAssetProvider {
    suspend fun getAssets(offset: Int, limit: Int): List<AgentAsset>
}

