package com.smith.lai.smithtoolcalls.langgraph.state

import org.json.JSONObject

/**
 * 基礎狀態類，只包含控制屬性和通用方法
 * 不包含與業務相關的數據欄位
 */
open class BaseState(
    // 控制屬性
    var completed: Boolean = false,
    var error: String? = null,
    var stepCount: Int = 0,
    val maxSteps: Int = 50,
    val startTime: Long = System.currentTimeMillis(),

    // 自訂數據容器
    val data: JSONObject = JSONObject()
) {
    /**
     * 增加步驟計數
     */
    fun incrementStep(): BaseState {
        stepCount++
        return this
    }

    /**
     * 設置錯誤訊息
     */
    fun withError(errorMessage: String): BaseState {
        error = errorMessage
        return this
    }

    /**
     * 設置完成狀態
     */
    fun withCompleted(isCompleted: Boolean): BaseState {
        completed = isCompleted
        return this
    }

    /**
     * 檢查是否達到最大步驟數
     */
    fun maxStepsReached(): Boolean = stepCount >= maxSteps

    /**
     * 計算執行時間
     */
    fun executionDuration(): Long = System.currentTimeMillis() - startTime

    // 自訂數據訪問方法

    /**
     * 設置自訂字串數據
     */
    fun setString(key: String, value: String): BaseState {
        data.put(key, value)
        return this
    }

    /**
     * 獲取自訂字串數據
     */
    fun getString(key: String, defaultValue: String = ""): String {
        return if (data.has(key)) data.getString(key) else defaultValue
    }

    /**
     * 設置自訂整數數據
     */
    fun setInt(key: String, value: Int): BaseState {
        data.put(key, value)
        return this
    }

    /**
     * 獲取自訂整數數據
     */
    fun getInt(key: String, defaultValue: Int = 0): Int {
        return if (data.has(key)) data.getInt(key) else defaultValue
    }

    /**
     * 設置自訂布林數據
     */
    fun setBoolean(key: String, value: Boolean): BaseState {
        data.put(key, value)
        return this
    }

    /**
     * 獲取自訂布林數據
     */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return if (data.has(key)) data.getBoolean(key) else defaultValue
    }

    /**
     * 設置自訂 JSON 物件
     */
    fun setObject(key: String, value: JSONObject): BaseState {
        data.put(key, value)
        return this
    }

    /**
     * 獲取自訂 JSON 物件
     */
    fun getObject(key: String): JSONObject? {
        return if (data.has(key)) data.getJSONObject(key) else null
    }

    /**
     * 檢查是否包含指定鍵
     */
    fun has(key: String): Boolean {
        return data.has(key)
    }
}
