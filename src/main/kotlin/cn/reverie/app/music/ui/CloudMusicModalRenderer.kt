package cn.reverie.app.music.ui

import cn.reverie.app.music.CloudMusicManager
import cn.reverie.app.music.api.QRStatus
import cn.reverie.app.music.render.CloudMusicImageCache
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer

/**
 * QR Code Login Modal Renderer for Nocturne Music.
 * Styled after ClickGUI's modal dialogs with backdrop blur, shadow, and smooth entrance.
 */
internal fun CloudMusicScreen.renderQRModal(mx: Float, my: Float, alpha: Float) {
    qrModalAnim.animateTo(if (showQRModal) 1f else 0f)
    val modalAlpha = qrModalAnim.value * alpha
    if (modalAlpha <= 0.01f) return

    val w = CloudMusicScreen.W
    val h = CloudMusicScreen.H

    // Dim backdrop over window
    NanoVGRenderer.drawRoundedRect(0f, 0f, w, h, 13f,
        NanoVGColor(0f, 0f, 0f, 0.65f * modalAlpha))

    // Centered Dialog Card (260 x 290)
    val cardW = 260f
    val cardH = 290f
    val cardX = (w - cardW) * 0.5f
    val cardY = (h - cardH) * 0.5f

    // Card Drop Shadow & Glass Background
    if (HUDModule.widgetShadow.value) {
        Draw2D.shadow(cardX, cardY + 4f, cardW, cardH, 12f, 24f,
            NanoVGColor(0f, 0f, 0f, 0.60f * modalAlpha))
    }
    NanoVGRenderer.drawRoundedRect(cardX, cardY, cardW, cardH, 12f,
        NanoVGColor(0.07f, 0.08f, 0.11f, 0.96f * modalAlpha))
    NanoVGRenderer.drawRoundedOutline(cardX, cardY, cardW, cardH, 12f,
        NanoVGColor(1f, 1f, 1f, 0.12f * modalAlpha), 1f)

    // Modal Header
    NanoVGRenderer.drawString("QR Code Login", cardX + 18f, cardY + 23f,
        NanoVGColor.WHITE.withAlpha(modalAlpha), 11.5f)

    // Modal Close Button
    val closeHovered = inside(mx, my, cardX + cardW - 32f, cardY + 12f, 22f, 22f)
    NanoVGRenderer.drawCenteredString("×", cardX + cardW - 21f, cardY + 23f,
        if (closeHovered) NanoVGColor.WHITE.withAlpha(modalAlpha) else NanoVGColor(0.68f, 0.70f, 0.75f, modalAlpha), 16f)

    Draw2D.line(cardX, cardY + 38f, cardX + cardW, cardY + 38f,
        NanoVGColor(1f, 1f, 1f, 0.07f * modalAlpha), 1f)

    // QR Code Image Container (140 x 140)
    val qrX = cardX + (cardW - 140f) * 0.5f
    val qrY = cardY + 54f

    NanoVGRenderer.drawRoundedRect(qrX - 4f, qrY - 4f, 148f, 148f, 8f,
        NanoVGColor(1f, 1f, 1f, 0.95f * modalAlpha))

    val bytes = qrBytes
    val key = qrKey
    if (bytes != null && key != null) {
        CloudMusicImageCache.drawBytesImage("qr_$key", bytes, qrX, qrY, 140f, 140f, radius = 4f, alpha = modalAlpha)
    } else {
        NanoVGRenderer.drawCenteredString("Generating QR Code...", qrX + 70f, qrY + 70f,
            NanoVGColor(0.20f, 0.20f, 0.25f, modalAlpha), 9.5f)
    }

    // Status Message below QR
    val statusColor = when (qrStatus) {
        QRStatus.SUCCESS -> NanoVGColor(0.35f, 0.85f, 0.45f, modalAlpha)
        QRStatus.EXPIRED, QRStatus.ERROR -> NanoVGColor(0.92f, 0.35f, 0.35f, modalAlpha)
        QRStatus.CONFIRMING -> NanoVGColor(0.95f, 0.80f, 0.30f, modalAlpha)
        QRStatus.WAITING -> NanoVGColor(0.72f, 0.74f, 0.78f, modalAlpha)
    }
    NanoVGRenderer.drawCenteredString(qrStatusMsg, cardX + cardW * 0.5f, cardY + 225f, statusColor, 9f)

    // Refresh Button if Expired or Error
    if (qrStatus == QRStatus.EXPIRED || qrStatus == QRStatus.ERROR) {
        val refreshHover = inside(mx, my, cardX + 60f, cardY + 246f, cardW - 120f, 26f)
        NanoVGRenderer.drawRoundedRect(cardX + 60f, cardY + 246f, cardW - 120f, 26f, 6f,
            if (refreshHover) NanoVGColor(1f, 1f, 1f, 0.16f * modalAlpha) else NanoVGColor(1f, 1f, 1f, 0.08f * modalAlpha))
        NanoVGRenderer.drawCenteredString("Refresh QR Code", cardX + cardW * 0.5f, cardY + 259f,
            NanoVGColor.WHITE.withAlpha(modalAlpha), 9f)
    } else {
        NanoVGRenderer.drawCenteredString("Open NetEase Cloud Music App to scan", cardX + cardW * 0.5f, cardY + 255f,
            NanoVGColor(0.48f, 0.50f, 0.55f, modalAlpha), 8f)
    }
}
