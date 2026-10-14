package com.r17e.guide.session

import com.r17e.guide.live.FunctionCall
import org.json.JSONArray
import org.json.JSONObject

/** Executes model tool calls locally and returns the JSON response sent back to the model. */
interface ToolHandler {
    val declarations: JSONArray
    fun handle(call: FunctionCall): JSONObject
    fun cancel(ids: List<String>) {}
}
