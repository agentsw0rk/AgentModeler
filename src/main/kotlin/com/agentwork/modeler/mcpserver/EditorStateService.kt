package com.agentwork.modeler.mcpserver

import com.agentwork.modeler.export.model.*
import com.agentwork.modeler.mcpserver.model.EditorEvent
import org.springframework.stereotype.Service
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Service
class EditorStateService {

    companion object {
        val VALID_NODE_TYPES = setOf("agent", "tool", "builtin-tool", "sub-agent", "mcp-server", "planner-agent")
        val VALID_SOURCE_TYPES = setOf("tool", "builtin-tool", "sub-agent", "mcp-server")
        val VALID_TARGET_TYPES = setOf("agent", "sub-agent", "planner-agent")
    }

    private val lock = ReentrantReadWriteLock()
    private val nodeCounter = AtomicInteger(0)
    private val edgeCounter = AtomicInteger(0)

    private val nodes = mutableMapOf<String, GraphNode>()
    private val edges = mutableMapOf<String, GraphEdge>()
    private var workspaceName = "Untitled Workspace"

    private val subscribers = CopyOnWriteArrayList<(EditorEvent) -> Unit>()

    fun subscribe(listener: (EditorEvent) -> Unit) {
        subscribers.add(listener)
    }

    fun unsubscribe(listener: (EditorEvent) -> Unit) {
        subscribers.remove(listener)
    }

    private fun emit(event: EditorEvent) {
        subscribers.forEach { it(event) }
    }

    fun getGraph(): WorkspaceGraph = lock.read {
        WorkspaceGraph(
            version = 1,
            name = workspaceName,
            nodes = nodes.values.toList(),
            edges = edges.values.toList()
        )
    }

    fun getNode(nodeId: String): GraphNode? = lock.read {
        nodes[nodeId]
    }

    fun createNode(type: String, x: Double, y: Double, data: Map<String, Any?>): String {
        require(type in VALID_NODE_TYPES) { "Invalid node type: $type" }
        val nodeId = "node-${nodeCounter.incrementAndGet()}"
        val node = GraphNode(id = nodeId, type = type, position = Position(x, y), data = data)
        lock.write {
            nodes[nodeId] = node
        }
        emit(EditorEvent.NodeCreated(node))
        return nodeId
    }

    fun createEdge(sourceId: String, targetId: String): String {
        val edgeId: String
        val edge: GraphEdge
        lock.write {
            val source = nodes[sourceId] ?: throw IllegalArgumentException("Source node not found: $sourceId")
            val target = nodes[targetId] ?: throw IllegalArgumentException("Target node not found: $targetId")
            require(source.type in VALID_SOURCE_TYPES) { "Invalid source type: ${source.type}. Must be one of $VALID_SOURCE_TYPES" }
            require(target.type in VALID_TARGET_TYPES) { "Invalid target type: ${target.type}. Must be one of $VALID_TARGET_TYPES" }
            edgeId = "edge-${edgeCounter.incrementAndGet()}"
            edge = GraphEdge(id = edgeId, source = sourceId, target = targetId)
            edges[edgeId] = edge
        }
        emit(EditorEvent.EdgeCreated(edge))
        return edgeId
    }

    fun updateNode(nodeId: String, data: Map<String, Any?>) {
        val mergedData: Map<String, Any?>
        lock.write {
            val existing = nodes[nodeId] ?: throw IllegalArgumentException("Node not found: $nodeId")
            mergedData = existing.data + data
            nodes[nodeId] = existing.copy(data = mergedData)
        }
        emit(EditorEvent.NodeUpdated(nodeId, mergedData))
    }

    fun deleteNode(nodeId: String) {
        val removedEdgeIds: List<String>
        lock.write {
            nodes.remove(nodeId) ?: throw IllegalArgumentException("Node not found: $nodeId")
            removedEdgeIds = edges.values
                .filter { it.source == nodeId || it.target == nodeId }
                .map { it.id }
            removedEdgeIds.forEach { edges.remove(it) }
        }
        removedEdgeIds.forEach { emit(EditorEvent.EdgeDeleted(it)) }
        emit(EditorEvent.NodeDeleted(nodeId))
    }

    fun deleteEdge(edgeId: String) {
        lock.write {
            edges.remove(edgeId) ?: throw IllegalArgumentException("Edge not found: $edgeId")
        }
        emit(EditorEvent.EdgeDeleted(edgeId))
    }

    fun setWorkspaceName(name: String) {
        lock.write {
            workspaceName = name
        }
        emit(EditorEvent.WorkspaceRenamed(name))
    }

    fun resetGraph() {
        val graph: WorkspaceGraph
        lock.write {
            nodes.clear()
            edges.clear()
            workspaceName = "Untitled Workspace"
            nodeCounter.set(0)
            edgeCounter.set(0)
            graph = WorkspaceGraph(version = 1, name = workspaceName, nodes = emptyList(), edges = emptyList())
        }
        emit(EditorEvent.GraphReset(graph))
    }

    fun syncGraph(graph: WorkspaceGraph) {
        lock.write {
            nodes.clear()
            edges.clear()
            workspaceName = graph.name
            graph.nodes.forEach { nodes[it.id] = it }
            graph.edges.forEach { edges[it.id] = it }

            val maxNodeId = graph.nodes
                .mapNotNull { it.id.removePrefix("node-").toIntOrNull() }
                .maxOrNull() ?: 0
            val maxEdgeId = graph.edges
                .mapNotNull { it.id.removePrefix("edge-").toIntOrNull() }
                .maxOrNull() ?: 0
            nodeCounter.set(maxNodeId)
            edgeCounter.set(maxEdgeId)
        }
        emit(EditorEvent.GraphReset(graph))
    }
}
