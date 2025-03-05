package com.smith.lai.smithtoolcalls.langgraph

import android.util.Log
import com.smith.lai.smithtoolcalls.langgraph.node.GraphNode
import com.smith.lai.smithtoolcalls.langgraph.node.Node
import com.smith.lai.smithtoolcalls.langgraph.node.NodeTypes
import com.smith.lai.smithtoolcalls.langgraph.node.StateGraph
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

/**
 * StateGraphBuilder for Python-like graph creation
 */
class StateGraphBuilder(private val stateClass: KClass<*> = StateGraph::class) {
    private val builder = LangGraph.builder()
    private val nodes = mutableMapOf<String, Node<StateGraph>>()

    /**
     * Add a node to the graph
     */
    fun addNode(name: String, node: Node<StateGraph>): StateGraphBuilder {
        nodes[name] = node
        builder.addNode(name, node)
        return this
    }

    /**
     * Add a node to the graph using NodeTypes enum
     */
    fun addNode(type: NodeTypes, node: Node<StateGraph>): StateGraphBuilder {
        return addNode(type.id, node)
    }

    /**
     * Add a direct edge between nodes
     */
    fun addEdge(source: String, target: String): StateGraphBuilder {
        builder.addEdge(source, target)
        return this
    }

    /**
     * Add a direct edge between nodes using NodeTypes enum
     */
    fun addEdge(source: NodeTypes, target: NodeTypes): StateGraphBuilder {
        return addEdge(source.id, target.id)
    }

    /**
     * Add a direct edge from node name to NodeTypes enum
     */
    fun addEdge(source: String, target: NodeTypes): StateGraphBuilder {
        return addEdge(source, target.id)
    }

    /**
     * Add a direct edge from NodeTypes enum to node name
     */
    fun addEdge(source: NodeTypes, target: String): StateGraphBuilder {
        return addEdge(source.id, target)
    }

    /**
     * Add conditional edges (Python-style)
     */
    fun addConditionalEdges(
        source: String,
        destinationMap: Map<(StateGraph) -> Boolean, String>,
        default: String? = null
    ): StateGraphBuilder {
        builder.addConditionalEdges(source, destinationMap, default)
        return this
    }

    /**
     * Add conditional edges with NodeTypes enum for source and default
     */
    fun addConditionalEdges(
        source: NodeTypes,
        destinationMap: Map<(StateGraph) -> Boolean, String>,
        default: NodeTypes? = null
    ): StateGraphBuilder {
        return addConditionalEdges(source.id, destinationMap, default?.id)
    }

    /**
     * Add conditional edges with NodeTypes enum for destinations
     */
    fun addConditionalEdge(
        source: String,
        destinationMap: Map<(StateGraph) -> Boolean, NodeTypes>,
        default: String? = null
    ): StateGraphBuilder {
        val stringMap = destinationMap.mapValues { it.value.id }
        return addConditionalEdges(source, stringMap, default)
    }

    /**
     * Add conditional edges with NodeTypes enum for everything
     */
    fun addConditionalEdge(
        source: NodeTypes,
        destinationMap: Map<(StateGraph) -> Boolean, NodeTypes>,
        default: NodeTypes? = null
    ): StateGraphBuilder {
        val stringMap = destinationMap.mapValues { it.value.id }
        return addConditionalEdges(source.id, stringMap, default?.id)
    }

    /**
     * Set channel for node (Python-style - for compatibility)
     */
    fun setNodeChannel(nodeName: String, channel: String = "default"): StateGraphBuilder {
        Log.d("StateGraphBuilder", "setNodeChannel is a no-op in Kotlin implementation")
        return this
    }

    /**
     * Set the entry point of the graph
     */
    fun setEntryPoint(nodeName: String): StateGraphBuilder {
        builder.setStartNode(nodeName)
        return this
    }

    /**
     * Set the entry point using NodeTypes enum
     */
    fun setEntryPoint(nodeType: NodeTypes): StateGraphBuilder {
        return setEntryPoint(nodeType.id)
    }

    /**
     * Compile the graph (Python-style)
     */
    fun compile(): LangGraph {
        // Ensure required nodes exist
        if (!nodes.containsKey(NodeTypes.START.id) && !nodes.containsKey(NodeTypes.END.id)) {
            Log.w("StateGraphBuilder", "No START or END nodes defined, adding default nodes")
            val startNodeClass = Class.forName("com.smith.lai.smithtoolcalls.langgraph.node.StartNode").kotlin
            val endNodeClass = Class.forName("com.smith.lai.smithtoolcalls.langgraph.node.EndNode").kotlin

            try {
                val startNodeInstance = startNodeClass.constructors.first().call() as Node<StateGraph>
                val endNodeInstance = endNodeClass.constructors.first().call() as Node<StateGraph>

                addNode(NodeTypes.START, startNodeInstance)
                addNode(NodeTypes.END, endNodeInstance)
            } catch (e: Exception) {
                Log.e("StateGraphBuilder", "Failed to create default nodes: ${e.message}")
                throw IllegalStateException("Failed to create default START/END nodes")
            }
        }

        return builder.build()
    }
}

/**
 * Core LangGraph implementation
 */
class LangGraph private constructor(
    private val nodes: Map<String, Node<StateGraph>>,
    private val edges: Map<String, Map<(StateGraph) -> Boolean, String>>,
    private val startNodeName: String,
    private val endNodeName: String,
    private val defaultEdges: Map<String, String>
) {
    companion object {
        /**
         * Create a builder for constructing a LangGraph
         */
        fun builder(): Builder {
            return Builder()
        }

        /**
         * Get node name from class annotation or simple name
         */
        fun getNodeName(nodeClass: KClass<out Node<*>>): String {
            return nodeClass.findAnnotation<GraphNode>()?.name ?: nodeClass.simpleName?.lowercase() ?: "unknown"
        }
    }

    /**
     * Execute the graph with a specific query
     */
    suspend fun invoke(query: String): StateGraph {
        return run(query)
    }

    /**
     * Execute the graph with a specific query (alias to match Python's __call__)
     */
    suspend fun run(query: String): StateGraph {
        var state = StateGraph(query = query)
        var currentNodeName = startNodeName

        Log.d("LangGraph", "Starting graph execution with query: $query")

        while (!state.completed) {
            // Increment step counter
            state = state.incrementStep()

            // Check for maximum steps
            if (state.maxStepsReached()) {
                Log.e("LangGraph", "Maximum steps (${state.maxSteps}) reached - terminating execution")
                state = state.copy(
                    error = "Maximum execution steps (${state.maxSteps}) exceeded",
                    completed = true
                )

                // Force jump to end node
                currentNodeName = endNodeName
            }

            // Get current node or terminate if not found
            val currentNode = nodes[currentNodeName]
            if (currentNode == null) {
                Log.e("LangGraph", "Node '$currentNodeName' not found, ending execution")
                state = state.copy(
                    error = "Node '$currentNodeName' not found",
                    completed = true
                )
                break
            }

            Log.d("LangGraph", "Step ${state.stepCount}: Executing node '$currentNodeName'")

            // Process current node
            val startTime = System.currentTimeMillis()
            state = currentNode.invoke(state)
            val duration = System.currentTimeMillis() - startTime

            Log.d("LangGraph", "Step ${state.stepCount}: Node '$currentNodeName' executed in ${duration}ms. " +
                    "State completed=${state.completed}, error=${state.error}")

            if (state.completed || currentNodeName == endNodeName) {
                // Ensure we process the end node if we're in an error state
                if (state.error != null && currentNodeName != endNodeName) {
                    Log.e("LangGraph", "Error detected, jumping to end node")
                    currentNodeName = endNodeName
                    continue
                }

                Log.d("LangGraph", "State marked as completed, ending graph execution")
                break
            }

            // Error handling - jump to end node if error detected
            if (state.error != null) {
                Log.e("LangGraph", "Error detected: ${state.error}, jumping to end node")
                currentNodeName = endNodeName
                continue
            }

            // Find next node
            val nextNodeName = findNextNode(currentNodeName, state)

            // Prevent infinite loops by detecting cycles
            if (nextNodeName == currentNodeName && state.stepCount > 5) {
                Log.e("LangGraph", "Potential infinite loop detected, jumping to end node")
                state = state.copy(
                    error = "Potential infinite loop detected",
                    completed = false
                )
                currentNodeName = endNodeName
                continue
            }

            Log.d("LangGraph", "Step ${state.stepCount}: Transitioning from '$currentNodeName' to '$nextNodeName'")
            currentNodeName = nextNodeName
        }

        val duration = state.executionDuration()
        Log.d("LangGraph", "Graph execution complete after ${state.stepCount} steps in ${duration}ms. " +
                "Final response length: ${state.finalResponse.length}")

        return state
    }

    /**
     * Find the next node to transition to
     */
    private fun findNextNode(currentNodeName: String, state: StateGraph): String {
        // Get conditional edges for this node
        val conditionalEdges = edges[currentNodeName]

        if (conditionalEdges != null) {
            // Try each condition in order
            for ((condition, targetNode) in conditionalEdges) {
                if (condition(state)) {
                    Log.d("LangGraph", "Edge condition met: $currentNodeName -> $targetNode")
                    return targetNode
                }
            }
        }

        // If no conditions match, use default edge if exists
        val defaultTarget = defaultEdges[currentNodeName]
        if (defaultTarget != null) {
            Log.d("LangGraph", "Using default edge: $currentNodeName -> $defaultTarget")
            return defaultTarget
        }

        // Otherwise stay at current node
        Log.d("LangGraph", "No matching edge conditions, staying at current node: $currentNodeName")
        return currentNodeName
    }

    /**
     * Builder for creating LangGraph instances
     */
    class Builder {
        private val nodes = mutableMapOf<String, Node<StateGraph>>()
        private val edges = mutableMapOf<String, MutableMap<(StateGraph) -> Boolean, String>>()
        private val defaultEdges = mutableMapOf<String, String>()
        private var startNodeName = NodeTypes.START.id
        private var endNodeName = NodeTypes.END.id

        /**
         * Add a node to the graph
         */
        fun addNode(name: String, node: Node<StateGraph>): Builder {
            nodes[name] = node
            return this
        }

        /**
         * Add a node using NodeTypes enum
         */
        fun addNode(type: NodeTypes, node: Node<StateGraph>): Builder {
            return addNode(type.id, node)
        }

        /**
         * Add a node using annotation or class name
         */
        fun addNode(node: Node<StateGraph>): Builder {
            val name = node::class.findAnnotation<GraphNode>()?.name
                ?: node::class.simpleName?.lowercase()
                ?: throw IllegalArgumentException("Node must have a GraphNode annotation or class name")

            nodes[name] = node
            return this
        }

        /**
         * Add a direct edge between nodes (matches Python's add_edge)
         */
        fun addEdge(source: String, target: String): Builder {
            defaultEdges[source] = target
            return this
        }

        /**
         * Add a direct edge between nodes using NodeTypes enum
         */
        fun addEdge(source: NodeTypes, target: NodeTypes): Builder {
            return addEdge(source.id, target.id)
        }

        /**
         * Add a direct edge from node name to NodeTypes
         */
        fun addEdge(source: String, target: NodeTypes): Builder {
            return addEdge(source, target.id)
        }

        /**
         * Add a direct edge from NodeTypes to node name
         */
        fun addEdge(source: NodeTypes, target: String): Builder {
            return addEdge(source.id, target)
        }

        /**
         * Add conditional edges based on state (matches Python's add_conditional_edges)
         */
        fun addConditionalEdges(
            source: String,
            conditionMap: Map<(StateGraph) -> Boolean, String>,
            defaultTarget: String? = null
        ): Builder {
            // Create or get the conditions map for the source node
            val nodeEdges = edges.getOrPut(source) { mutableMapOf() }

            // Add all the condition-to-destination mappings
            nodeEdges.putAll(conditionMap)

            // Set default edge if provided
            if (defaultTarget != null) {
                defaultEdges[source] = defaultTarget
            }

            return this
        }

        /**
         * Add conditional edges with NodeTypes enum for source and default
         */
        fun addConditionalEdges(
            source: NodeTypes,
            conditionMap: Map<(StateGraph) -> Boolean, String>,
            defaultTarget: NodeTypes? = null
        ): Builder {
            return addConditionalEdges(source.id, conditionMap, defaultTarget?.id)
        }

        /**
         * Add conditional edges with NodeTypes enum for destinations
         */
        fun addConditionalEdgesEnum(
            source: String,
            conditionMap: Map<(StateGraph) -> Boolean, NodeTypes>,
            defaultTarget: String? = null
        ): Builder {
            val stringMap = conditionMap.mapValues { it.value.id }
            return addConditionalEdges(source, stringMap, defaultTarget)
        }

        /**
         * Add conditional edges with NodeTypes enum for everything
         */
        fun addConditionalEdgesEnum(
            source: NodeTypes,
            conditionMap: Map<(StateGraph) -> Boolean, NodeTypes>,
            defaultTarget: NodeTypes? = null
        ): Builder {
            val stringMap = conditionMap.mapValues { it.value.id }
            return addConditionalEdges(source.id, stringMap, defaultTarget?.id)
        }

        /**
         * Simplified conditional edge for binary condition (convenience method)
         */
        fun addConditionalEdge(
            source: String,
            targetIfTrue: String,
            targetIfFalse: String,
            condition: (StateGraph) -> Boolean
        ): Builder {
            val conditionMap = mapOf(
                condition to targetIfTrue
            )

            // Add the condition mapping and set the default (false case)
            addConditionalEdges(source, conditionMap, targetIfFalse)

            return this
        }

        /**
         * Simplified conditional edge with NodeTypes enum
         */
        fun addConditionalEdge(
            source: NodeTypes,
            targetIfTrue: NodeTypes,
            targetIfFalse: NodeTypes,
            condition: (StateGraph) -> Boolean
        ): Builder {
            return addConditionalEdge(source.id, targetIfTrue.id, targetIfFalse.id, condition)
        }

        /**
         * Set the start node name
         */
        fun setStartNode(name: String): Builder {
            startNodeName = name
            return this
        }

        /**
         * Set the start node using NodeTypes enum
         */
        fun setStartNode(nodeType: NodeTypes): Builder {
            return setStartNode(nodeType.id)
        }

        /**
         * Set the end node name
         */
        fun setEndNode(name: String): Builder {
            endNodeName = name
            return this
        }

        /**
         * Set the end node using NodeTypes enum
         */
        fun setEndNode(nodeType: NodeTypes): Builder {
            return setEndNode(nodeType.id)
        }

        /**
         * Build the LangGraph instance
         */
        fun build(): LangGraph {
            // Ensure start and end nodes exist
            if (!nodes.containsKey(startNodeName)) {
                throw IllegalStateException("Start node '$startNodeName' not defined")
            }

            if (!nodes.containsKey(endNodeName)) {
                throw IllegalStateException("End node '$endNodeName' not defined")
            }

            return LangGraph(
                nodes = nodes,
                edges = edges,
                startNodeName = startNodeName,
                endNodeName = endNodeName,
                defaultEdges = defaultEdges
            )
        }
    }
}