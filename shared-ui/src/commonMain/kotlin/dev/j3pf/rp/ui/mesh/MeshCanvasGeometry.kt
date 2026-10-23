package dev.j3pf.rp.ui.mesh

import dev.j3pf.rp.ui.mesh.layout.LayoutBounds

fun safeCommBubbleCenterY(
    midpointY: Float,
    senderBottom: Float,
    receiverTop: Float,
    otherCards: List<LayoutBounds>,
    bubbleCenterX: Float,
    bubbleHalfWidth: Float,
): Float {
    var safeCenterY = safeCommBubbleCenterY(midpointY, senderBottom, receiverTop)
    val bubbleLeft = bubbleCenterX - bubbleHalfWidth
    val bubbleRight = bubbleCenterX + bubbleHalfWidth

    var shifted = true
    while (shifted) {
        shifted = false
        for (card in otherCards) {
            val overlapsHorizontally = card.minX <= bubbleRight && card.maxX >= bubbleLeft
            val minimumClearCenterY = card.minY - 24f
            val maximumClearCenterY = card.maxY + 24f
            if (overlapsHorizontally && safeCenterY > minimumClearCenterY && safeCenterY < maximumClearCenterY) {
                safeCenterY = maximumClearCenterY
                shifted = true
            }
        }
    }
    return safeCenterY
}

fun safeCommBubbleCenterY(
    midpointY: Float,
    senderBottom: Float,
    receiverTop: Float,
): Float {
    val minimumY = senderBottom + 24f
    val maximumY = receiverTop - 24f
    return if (minimumY <= maximumY) midpointY.coerceIn(minimumY, maximumY) else maxOf(midpointY, minimumY)
}
