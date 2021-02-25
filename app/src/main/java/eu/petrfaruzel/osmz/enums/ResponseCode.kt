package eu.petrfaruzel.osmz.enums

enum class ResponseCode(val value : Int, val responseText : String) {
    CODE_200(200, "200 OK"),
    CODE_404(404, "404 Not found"),
    CODE_503(503,"503 Server too busy")
}