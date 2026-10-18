package com.tejeet.carddrop.util

/**
 * The white card outline in the scanner, as fractions (0..1) of the preview's size.
 * The overlay draws it and ImageUtils.cropToFrame crops with it, so what the user lines
 * up inside the frame is exactly what OCR gets.
 */
data class CardFrame(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    companion object {
        /** Standard business card: 3.5 x 2 in (85 x 55 mm is close enough). */
        private const val CARD_RATIO = 1.75f

        fun forView(viewWidth: Float, viewHeight: Float, vertical: Boolean): CardFrame {
            if (viewWidth <= 0f || viewHeight <= 0f) return CardFrame(0f, 0f, 1f, 1f)
            var w: Float
            var h: Float
            if (vertical) {
                h = viewHeight * 0.6f
                w = h / CARD_RATIO
                if (w > viewWidth * 0.8f) {
                    w = viewWidth * 0.8f
                    h = w * CARD_RATIO
                }
            } else {
                w = viewWidth * 0.9f
                h = w / CARD_RATIO
                if (h > viewHeight * 0.6f) {
                    h = viewHeight * 0.6f
                    w = h * CARD_RATIO
                }
            }
            // Slightly above centre, leaving room for the shutter controls.
            val cx = viewWidth / 2f
            val cy = viewHeight * 0.42f
            return CardFrame(
                left = (cx - w / 2f) / viewWidth,
                top = (cy - h / 2f) / viewHeight,
                right = (cx + w / 2f) / viewWidth,
                bottom = (cy + h / 2f) / viewHeight
            )
        }
    }
}
