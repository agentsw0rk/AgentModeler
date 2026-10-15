package com.agentwork.modeler.ollama

import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMCapability
import com.agentwork.modeler.export.generator.OllamaModelMapping
import kotlinx.coroutines.withTimeout
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

fun interface OllamaModelSource {
    suspend fun models(): List<OllamaModelInfo>
}

/**
 * Lists the models installed in the local Ollama for the editor's model dropdown.
 * Uses the same OLLAMA_BASE_URL as the live preview; the URL is deliberately not
 * taken from the request so the endpoint can't be used to probe arbitrary hosts.
 */
@Service
class OllamaDiscoveryService(
    private val baseUrl: String,
    private val source: OllamaModelSource
) {

    @Autowired
    constructor() : this(System.getenv(OllamaModelMapping.BASE_URL_ENV) ?: OllamaModelMapping.DEFAULT_BASE_URL)

    private constructor(baseUrl: String) : this(baseUrl, KoogOllamaModelSource(baseUrl))

    suspend fun listModels(): OllamaModelsResponse = try {
        val models = withTimeout(DISCOVERY_TIMEOUT_MS) { source.models() }
        OllamaModelsResponse(models.map { it.toDto() })
    } catch (e: Exception) {
        OllamaModelsResponse(emptyList(), "Ollama not reachable at $baseUrl: ${e.message}")
    }

    private fun OllamaModelInfo.toDto(): OllamaModelDto {
        val details = listOfNotNull(parameterCount?.let(::formatParameterCount), quantizationLevel)
        val label = if (details.isEmpty()) name else "$name (${details.joinToString(", ")})"
        return OllamaModelDto(id = name, label = label, tools = supportsTools)
    }

    private fun formatParameterCount(count: Long): String {
        val (value, unit) = if (count >= 1_000_000_000) count / 1e9 to "B" else count / 1e6 to "M"
        return BigDecimal(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + unit
    }

    companion object {
        private const val DISCOVERY_TIMEOUT_MS = 5_000L
    }
}

private class KoogOllamaModelSource(private val baseUrl: String) : OllamaModelSource {
    override suspend fun models(): List<OllamaModelInfo> {
        val client = OllamaClient(baseUrl = baseUrl)
        try {
            return client.getModels().map { card ->
                OllamaModelInfo(
                    name = card.name,
                    parameterCount = card.parameterCount,
                    quantizationLevel = card.quantizationLevel,
                    supportsTools = LLMCapability.Tools in card.capabilities
                )
            }
        } finally {
            client.close()
        }
    }
}
