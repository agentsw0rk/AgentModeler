package com.agentwork.modeler.export.validation

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class GraphValidatorTest {
    private val validator = GraphValidator()

    @Test
    fun `valid graph passes validation`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("t1", "tool", Position(200.0, 0.0), mapOf("name" to "Tool1", "description" to "Desc", "parameters" to listOf(mapOf("id" to "p1", "name" to "q", "type" to "String", "description" to "d", "required" to true))))
            ),
            listOf(GraphEdge("e1", "t1", "a1"))
        )
        assertTrue(validator.validate(graph).isEmpty())
    }

    @Test
    fun `reports agent without name`() {
        val graph = ProjectGraph(1, "Test",
            listOf(GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "", "systemPrompt" to "Help", "modelProvider" to "openai"))),
            emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("name") && it.contains("a1") })
    }

    @Test
    fun `reports agent without system prompt`() {
        val graph = ProjectGraph(1, "Test",
            listOf(GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "", "modelProvider" to "openai"))),
            emptyList())
        assertTrue(validator.validate(graph).any { it.contains("prompt") })
    }

    @Test
    fun `reports tool without name`() {
        val graph = ProjectGraph(1, "Test",
            listOf(GraphNode("t1", "tool", Position(0.0, 0.0), mapOf("name" to "", "description" to "Desc", "parameters" to emptyList<Any>()))),
            emptyList())
        assertTrue(validator.validate(graph).any { it.contains("name") && it.contains("t1") })
    }

    @Test
    fun `reports tool without connected agent`() {
        val graph = ProjectGraph(1, "Test",
            listOf(GraphNode("t1", "tool", Position(0.0, 0.0), mapOf("name" to "Tool1", "description" to "Desc", "parameters" to emptyList<Any>()))),
            emptyList())
        assertTrue(validator.validate(graph).any { it.contains("connected") })
    }

    @Test
    fun `reports parameter without name`() {
        val graph = ProjectGraph(1, "Test",
            listOf(GraphNode("t1", "tool", Position(0.0, 0.0), mapOf("name" to "Tool1", "description" to "Desc",
                "parameters" to listOf(mapOf("id" to "p1", "name" to "", "type" to "String", "description" to "", "required" to true))))),
            listOf(GraphEdge("e1", "t1", "a1")))
        assertTrue(validator.validate(graph).any { it.contains("Parameter") })
    }

    @Test
    fun `reports error for multiple agent nodes`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("a2", "agent", Position(300.0, 0.0), mapOf("name" to "Agent2", "systemPrompt" to "Help2", "modelProvider" to "anthropic"))
            ),
            emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("single-agent") || it.contains("Only single") })
    }

    @Test
    fun `valid builtin-tool passes validation`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("bt1", "builtin-tool", Position(200.0, 0.0), mapOf("toolType" to "shell"))
            ),
            listOf(GraphEdge("e1", "bt1", "a1")))
        assertTrue(validator.validate(graph).isEmpty())
    }

    @Test
    fun `reports builtin-tool without toolType`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("bt1", "builtin-tool", Position(200.0, 0.0), mapOf<String, Any?>())
            ),
            listOf(GraphEdge("e1", "bt1", "a1")))
        assertTrue(validator.validate(graph).any { it.contains("toolType") })
    }

    @Test
    fun `reports builtin-tool with invalid toolType`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("bt1", "builtin-tool", Position(200.0, 0.0), mapOf("toolType" to "invalid"))
            ),
            listOf(GraphEdge("e1", "bt1", "a1")))
        assertTrue(validator.validate(graph).any { it.contains("toolType") })
    }

    @Test
    fun `reports builtin-tool not connected to agent`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("bt1", "builtin-tool", Position(200.0, 0.0), mapOf("toolType" to "shell"))
            ),
            emptyList())
        assertTrue(validator.validate(graph).any { it.contains("connected") })
    }

    @Test
    fun `valid sub-agent connected to agent passes validation`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("sa1", "sub-agent", Position(200.0, 0.0), mapOf(
                    "name" to "ResearchAgent", "description" to "Researches topics",
                    "systemPrompt" to "You research.", "inputDescription" to "A topic",
                    "modelProvider" to "openai"
                ))
            ),
            listOf(GraphEdge("e1", "sa1", "a1")))
        assertTrue(validator.validate(graph).isEmpty())
    }

    @Test
    fun `reports sub-agent without name`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("sa1", "sub-agent", Position(200.0, 0.0), mapOf(
                    "name" to "", "description" to "Desc",
                    "systemPrompt" to "Prompt", "inputDescription" to "Input",
                    "modelProvider" to "openai"
                ))
            ),
            listOf(GraphEdge("e1", "sa1", "a1")))
        assertTrue(validator.validate(graph).any { it.contains("name") && it.contains("sa1") })
    }

    @Test
    fun `reports sub-agent without description`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("sa1", "sub-agent", Position(200.0, 0.0), mapOf(
                    "name" to "SA", "description" to "",
                    "systemPrompt" to "Prompt", "inputDescription" to "Input",
                    "modelProvider" to "openai"
                ))
            ),
            listOf(GraphEdge("e1", "sa1", "a1")))
        assertTrue(validator.validate(graph).any { it.contains("description") })
    }

    @Test
    fun `reports sub-agent without system prompt`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("sa1", "sub-agent", Position(200.0, 0.0), mapOf(
                    "name" to "SA", "description" to "Desc",
                    "systemPrompt" to "", "inputDescription" to "Input",
                    "modelProvider" to "openai"
                ))
            ),
            listOf(GraphEdge("e1", "sa1", "a1")))
        assertTrue(validator.validate(graph).any { it.contains("prompt") })
    }

    @Test
    fun `reports sub-agent without input description`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("sa1", "sub-agent", Position(200.0, 0.0), mapOf(
                    "name" to "SA", "description" to "Desc",
                    "systemPrompt" to "Prompt", "inputDescription" to "",
                    "modelProvider" to "openai"
                ))
            ),
            listOf(GraphEdge("e1", "sa1", "a1")))
        assertTrue(validator.validate(graph).any { it.contains("input description") })
    }

    @Test
    fun `reports sub-agent not connected to agent`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("sa1", "sub-agent", Position(200.0, 0.0), mapOf(
                    "name" to "SA", "description" to "Desc",
                    "systemPrompt" to "Prompt", "inputDescription" to "Input",
                    "modelProvider" to "openai"
                ))
            ),
            emptyList())
        assertTrue(validator.validate(graph).any { it.contains("connected") })
    }

    @Test
    fun `reports sub-agent connected to another sub-agent`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("sa1", "sub-agent", Position(200.0, 0.0), mapOf(
                    "name" to "SA1", "description" to "Desc",
                    "systemPrompt" to "Prompt", "inputDescription" to "Input",
                    "modelProvider" to "openai"
                )),
                GraphNode("sa2", "sub-agent", Position(400.0, 0.0), mapOf(
                    "name" to "SA2", "description" to "Desc",
                    "systemPrompt" to "Prompt", "inputDescription" to "Input",
                    "modelProvider" to "openai"
                ))
            ),
            listOf(GraphEdge("e1", "sa1", "a1"), GraphEdge("e2", "sa2", "sa1")))
        assertTrue(validator.validate(graph).any { it.contains("cannot delegate") || it.contains("Sub-Agent") })
    }

    @Test
    fun `sub-agent does not count toward single-agent limit`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("sa1", "sub-agent", Position(200.0, 0.0), mapOf(
                    "name" to "SA1", "description" to "Desc",
                    "systemPrompt" to "Prompt", "inputDescription" to "Input",
                    "modelProvider" to "openai"
                ))
            ),
            listOf(GraphEdge("e1", "sa1", "a1")))
        val errors = validator.validate(graph)
        assertFalse(errors.any { it.contains("single-agent") || it.contains("Only single") })
    }

    @Test
    fun `valid mcp-server connected to agent passes validation`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("mcp1", "mcp-server", Position(200.0, 0.0), mapOf(
                    "name" to "Google Maps", "transportType" to "sse",
                    "url" to "http://localhost:8931"
                ))
            ),
            listOf(GraphEdge("e1", "mcp1", "a1")))
        assertTrue(validator.validate(graph).isEmpty())
    }

    @Test
    fun `reports mcp-server without name`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("mcp1", "mcp-server", Position(200.0, 0.0), mapOf(
                    "name" to "", "transportType" to "sse",
                    "url" to "http://localhost:8931"
                ))
            ),
            listOf(GraphEdge("e1", "mcp1", "a1")))
        assertTrue(validator.validate(graph).any { it.contains("name") && it.contains("mcp1") })
    }

    @Test
    fun `reports mcp-server with invalid transport type`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("mcp1", "mcp-server", Position(200.0, 0.0), mapOf(
                    "name" to "Server", "transportType" to "invalid",
                    "url" to "http://localhost:8931"
                ))
            ),
            listOf(GraphEdge("e1", "mcp1", "a1")))
        assertTrue(validator.validate(graph).any { it.contains("transport") })
    }

    @Test
    fun `reports sse mcp-server without url`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("mcp1", "mcp-server", Position(200.0, 0.0), mapOf(
                    "name" to "Server", "transportType" to "sse", "url" to ""
                ))
            ),
            listOf(GraphEdge("e1", "mcp1", "a1")))
        assertTrue(validator.validate(graph).any { it.contains("URL") })
    }

    @Test
    fun `reports stdio mcp-server without command`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("mcp1", "mcp-server", Position(200.0, 0.0), mapOf(
                    "name" to "Server", "transportType" to "stdio", "command" to ""
                ))
            ),
            listOf(GraphEdge("e1", "mcp1", "a1")))
        assertTrue(validator.validate(graph).any { it.contains("command") })
    }

    @Test
    fun `reports mcp-server not connected to agent`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("mcp1", "mcp-server", Position(200.0, 0.0), mapOf(
                    "name" to "Server", "transportType" to "sse", "url" to "http://localhost"
                ))
            ),
            emptyList())
        assertTrue(validator.validate(graph).any { it.contains("connected") })
    }

    @Test
    fun `mcp-server does not count toward single-agent limit`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("mcp1", "mcp-server", Position(200.0, 0.0), mapOf(
                    "name" to "Server", "transportType" to "sse", "url" to "http://localhost"
                ))
            ),
            listOf(GraphEdge("e1", "mcp1", "a1")))
        assertFalse(validator.validate(graph).any { it.contains("single-agent") || it.contains("Only single") })
    }
}
