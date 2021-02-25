package eu.petrfaruzel.osmz

import eu.petrfaruzel.osmz.enums.RequestType
import eu.petrfaruzel.osmz.enums.ResponseCode
import java.util.*

data class LogItem(
        val ipAddress : String,
        val date: Date,  // I would use threeten offsetdatetime, but for simplicity it's this disaster
        val requestType : RequestType,
        val path : String?,
        val httpVersion : String?,
        val responseCode : ResponseCode
) {
}