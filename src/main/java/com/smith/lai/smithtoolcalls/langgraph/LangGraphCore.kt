package com.smith.lai.smithtoolcalls.langgraph

import android.util.Log
import com.smith.lai.smithtoolcalls.langgraph.node.Node
import com.smith.lai.smithtoolcalls.langgraph.node.NodeTypes
import com.smith.lai.smithtoolcalls.langgraph.node.StateGraph
import kotlin.reflect.KClass

/**
 * StateGraphBuilder combines building and execution in one class
 * Directly matches Python LangGraph's API style
 */
class StateGraphBuilder {
    private val nodes = mutableMapOf<String, Node<StateGraph>>()
    private val edges = mutableMapOf<String, MutableMap<(StateGraph) -> Boolean, String>>()
    private val defaultEdges = mutableMapOf<String, String>()
    private var startNodeName = NodeTypes.START
    private var endNodeName = NodeTypes.END
    private var compiled = false

    /**
     * Add a node to the graph
     */
    fun addNode(name: String, node: Node<StateGraph>): StateGraphBuilder {
        nodes[name] = node
        return this
    }

    /**
     * Add a direct edge between nodes
     */
    fun addEdge(source: String, target: String): StateGraphBuilder {
        defaultEdges[source] = target
        return this
    }


    /**
     * Add conditional edges based on state
     */
    fun addConditionalEdges(
        source: String,
        conditionMap: Map<(StateGraph) -> Boolean, String>,
        defaultTarget: String? = null
    ): StateGraphBuilder {
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
     * Add conditional edge - simplified for Python-like API
     */
    fun addConditionalEdge(
        source: String,
        conditionMap: Map<(StateGraph) -> Boolean, String>,
        defaultTarget: String? = null
    ): StateGraphBuilder {
        return addConditionalEdges(source, conditionMap, defaultTarget)
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
        startNodeName = nodeName
        return this
    }

    /**
     * Ensure required nodes exist
     */
    private fun ensureRequiredNodes() {
        if (!nodes.containsKey(startNodeName) || !nodes.containsKey(endNodeName)) {
            Log.w("StateGraphBuilder", "START or END nodes missing, trying to add default nodes")

            if (!nodes.containsKey(startNodeName)) {
                try {
                    val startNodeClass = Class.forName("com.smith.lai.smithtoolcalls.langgraph.node.StartNode").kotlin
                    val startNodeInstance = startNodeClass.constructors.first().call() as Node<StateGraph>
                    addNode(startNodeName, startNodeInstance)
                } catch (e: Exception) {
                    Log.e("StateGraphBuilder", "Failed to create default START node: ${e.message}")
                    throw IllegalStateException("Start node '$startNodeName' not defined")
                }
            }

            if (!nodes.containsKey(endNodeName)) {
                try {
                    val endNodeClass = Class.forName("com.smith.lai.smithtoolcalls.langgraph.node.EndNode").kotlin
                    val endNodeInstance = endNodeClass.constructors.first().call() as Node<StateGraph>
                    addNode(endNodeName, endNodeInstance)
                } catch (e: Exception) {
                    Log.e("StateGraphBuilder", "Failed to create default END node: ${e.message}")
                    throw IllegalStateException("End node '$endNodeName' not defined")
                }
            }
        }
    }

    /**
     * Compile the graph (Python-style)
     * In this implementation, compile() marks the graph as executable
     * but doesn't create a new object
     */
    fun compile(): StateGraphBuilder {
        ensureRequiredNodes()
        compiled = true
        return this
    }

    /**
     * Execute the graph with a specific query
     */
    suspend fun invoke(query: String): StateGraph {
        return run(query)
    }

    /**
     * Execute the graph with a specific query
     */
    suspend fun run(query: String): StateGraph {
        if (!compiled) {
            Log.w("StateGraphBuilder", "Graph not compiled, compiling now")
            compile()
        }

        var state = StateGraph(query = query)
        var currentNodeName = startNodeName

        Log.d("StateGraphBuilder", "Starting graph execution with query: $query")

        while (!state.completed) {
            // Increment step counter
            state = state.incrementStep()

            // Check for maximum steps
            if (state.maxStepsReached()) {
                Log.e("StateGraphBuilder", "Maximum steps (${state.maxSteps}) reached - terminating execution")
                state = state.copy(
                    error = "Maximum execution steps (${state.maxSteps}) exceeded",
                    completed = true
                )
                currentNodeName = endNodeName
            }

            // Get current node or terminate if not found
            val currentNode = nodes[currentNodeName]
            if (currentNode == null) {
                Log.e("StateGraphBuilder", "Node '$currentNodeName' not found, ending execution")
                state = state.copy(
                    error = "Node '$currentNodeName' not found",
                    completed = true
                )
                break
            }

            Log.d("StateGraphBuilder", "Step ${state.stepCount}: Executing node '$currentNodeName'")

            // Process current node
            val startTime = System.currentTimeMillis()
            state = currentNode.invoke(state)
            val duration = System.currentTimeMillis() - startTime

            Log.d("StateGraphBuilder", "Step ${state.stepCount}: Node '$currentNodeName' executed in ${duration}ms. " +
                    "State completed=${state.completed}, error=${state.error}")

            if (state.completed || currentNodeName == endNodeName) {
                // Ensure we process the end node if we're in an error state
                if (state.error != null && currentNodeName != endNodeName) {
                    Log.e("StateGraphBuilder", "Error detected, jumping to end node")
                    currentNodeName = endNodeName
                    continue
                }

                Log.d("StateGraphBuilder", "State marked as completed, ending graph execution")
                break
            }

            // Error handling - jump to end node if error detected
            if (state.error != null) {
                Log.e("StateGraphBuilder", "Error detected: ${state.error}, jumping to end node")
                currentNodeName = endNodeName
                continue
            }

            // Find next node
            val nextNodeName = findNextNode(currentNodeName, state)

            // Prevent infinite loops by detecting cycles
            if (nextNodeName == currentNodeName && state.stepCount > 5) {
                Log.e("StateGraphBuilder", "Potential infinite loop detected, jumping to end node")
                state = state.copy(
                    error = "Potential infinite loop detected",
                    completed = false
                )
                currentNodeName = endNodeName
                continue
            }

            Log.d("StateGraphBuilder", "Step ${state.stepCount}: Transitioning from '$currentNodeName' to '$nextNodeName'")
            currentNodeName = nextNodeName
        }

        val duration = state.executionDuration()
        Log.d("StateGraphBuilder", "Graph execution complete after ${state.stepCount} steps in ${duration}ms. " +
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
                    Log.d("StateGraphBuilder", "Edge condition met: $currentNodeName -> $targetNode")
                    return targetNode
                }
            }
        }

        // If no conditions match, use default edge if exists
        val defaultTarget = defaultEdges[currentNodeName]
        if (defaultTarget != null) {
            Log.d("StateGraphBuilder", "Using default edge: $currentNodeName -> $defaultTarget")
            return defaultTarget
        }

        // Otherwise stay at current node
        Log.d("StateGraphBuilder", "No matching edge conditions, staying at current node: $currentNodeName")
        return currentNodeName
    }
}