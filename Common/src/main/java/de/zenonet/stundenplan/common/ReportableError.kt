package de.zenonet.stundenplan.common

import org.json.JSONObject

data class ReportableError(
    val errorType: String,
    val causingModule: String,
    val payload: String,
    var userId: Int,
    var appVersion: String
) {
    constructor(errorType: String, causingModule: String, payload: String) : this(errorType, causingModule, payload, -1, "unknown")

    override fun toString(): String {
        val data = JSONObject()

        data.put("type", errorType)
        data.put("version", appVersion)
        data.put("id", userId)
        data.put("causingModule", causingModule)
        data.put("payload", payload)
        return data.toString()
    }
}
