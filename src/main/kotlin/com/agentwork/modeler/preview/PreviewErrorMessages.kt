package com.agentwork.modeler.preview

private val ollamaModelNotFound = Regex("""model '([^']+)' not found""")
private val ollamaConnectFailed = Regex("""Connect to (\S+) .*failed: Connection refused""")

/**
 * Message shown in the preview for a failed agent run. Koog's raw Ollama errors
 * (missing model, Ollama not running) are turned into the command that fixes them.
 */
fun describePreviewError(e: Throwable): String {
    val message = e.message ?: return "Unknown error"
    if ("OllamaClient" !in message) return message

    ollamaModelNotFound.find(message)?.let { match ->
        val model = match.groupValues[1]
        return "Ollama model '$model' is not installed. Run: ollama pull $model"
    }
    ollamaConnectFailed.find(message)?.let { match ->
        return "Ollama is not reachable at ${match.groupValues[1]}. Start it with: ollama serve"
    }
    return message
}
