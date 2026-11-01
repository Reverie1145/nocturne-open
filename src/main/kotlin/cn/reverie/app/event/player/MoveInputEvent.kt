package cn.reverie.app.event.player

class MoveInputEvent(
    var forward: Float,
    var strafe: Float,
    var jump: Boolean,
    var sneak: Boolean,
    var sprint: Boolean
)
