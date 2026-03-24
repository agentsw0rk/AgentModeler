package com.agentwork.modeler.review

interface ReviewProvider {
    suspend fun review(request: ReviewRequest): ReviewResult
}
