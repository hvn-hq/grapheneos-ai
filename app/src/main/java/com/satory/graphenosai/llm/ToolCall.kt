package com.satory.graphenosai.llm

import org.json.JSONArray
import org.json.JSONObject

data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: ToolFunctionCall
)

data class ToolFunctionCall(
    val name: String,
    val arguments: String
)

object WebSearchTool {
    fun buildDefinition(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "web_search")
                put("description", "Search the web for current, real-time information. Use this for news, weather, prices, sports, stock data, recent events, facts you're not confident about, or any time-sensitive query. Always use the user's question verbatim as the search query.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("query", JSONObject().apply {
                            put("type", "string")
                            put("description", "The search query string")
                        })
                    })
                    put("required", JSONArray(listOf("query")))
                })
            })
        }
    }

    fun buildToolsArray(): JSONArray {
        return JSONArray().apply {
            put(buildDefinition())
        }
    }
}

fun parseSearchQuery(arguments: String): String {
    return try {
        JSONObject(arguments).optString("query", "").trim()
    } catch (e: Exception) {
        arguments.trim()
    }
}

fun buildToolAssistantMessage(toolCalls: List<ToolCall>): JSONObject {
    return JSONObject().apply {
        put("role", "assistant")
        put("content", JSONObject.NULL)
        val callsArray = JSONArray()
        for (tc in toolCalls) {
            callsArray.put(JSONObject().apply {
                put("id", tc.id)
                put("type", tc.type)
                put("function", JSONObject().apply {
                    put("name", tc.function.name)
                    put("arguments", tc.function.arguments)
                })
            })
        }
        put("tool_calls", callsArray)
    }
}

fun buildToolResultMessage(toolCallId: String, content: String): JSONObject {
    return JSONObject().apply {
        put("role", "tool")
        put("tool_call_id", toolCallId)
        put("content", content)
    }
}

sealed class StreamEvent {
    data class Content(val text: String) : StreamEvent()
    data class ToolCallsDetected(val assistantMessage: JSONObject, val calls: List<ToolCall>) : StreamEvent()
}
