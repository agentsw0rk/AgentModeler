package com.agentwork.modeler

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class IndexController(private val myAgentService: MyAgentService) {

    @PostMapping("/ask")
    suspend fun ask(@RequestBody request: AskRequest): AskResponse {
        val result = myAgentService.askAgent(request.prompt)
        return AskResponse(result)
    }
}

data class AskRequest(val prompt: String)

data class AskResponse(val response: String)
