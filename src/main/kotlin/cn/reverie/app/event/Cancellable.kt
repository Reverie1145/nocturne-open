package cn.reverie.app.event

interface Cancellable {
    var isCancelled: Boolean

    fun cancel() {
        isCancelled = true
    }
}
