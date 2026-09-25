package com.keenin.calbudget.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The few menu glyphs that are not in material-icons-core.
 * Paths are the standard 24dp Material filled icons.
 */
internal object AppIcons {
    val Receipt: ImageVector by lazy { icon(RECEIPT) }
    val CreditCard: ImageVector by lazy { icon(CREDIT_CARD) }
    val AccountBalance: ImageVector by lazy { icon(ACCOUNT_BALANCE) }
    val Payments: ImageVector by lazy { icon(PAYMENTS) }

    private fun icon(path: String): ImageVector {
        return ImageVector.Builder(
            name = "AppIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    private const val RECEIPT =
        "M18,17H6v-2h12V17zM18,13H6v-2h12V13zM18,9H6V7h12V9zM3,22l1.5,-1.5L6,22l1.5,-1.5L9,22l1.5,-1.5L12,22l1.5,-1.5L15,22l1.5,-1.5L18,22l1.5,-1.5L21,22V2l-1.5,1.5L18,2l-1.5,1.5L15,2l-1.5,1.5L12,2l-1.5,1.5L9,2L7.5,3.5L6,2L4.5,3.5L3,2V22z"

    private const val CREDIT_CARD =
        "M20,4H4C2.89,4 2.01,4.89 2.01,6L2,18c0,1.11 0.89,2 2,2h16c1.11,0 2,-0.89 2,-2V6C22,4.89 21.11,4 20,4zM20,18H4v-6h16V18zM20,8H4V6h16V8z"

    private const val ACCOUNT_BALANCE =
        "M4,10h3v7H4V10zM10.5,10h3v7h-3V10zM2,19h20v3H2V19zM17,10h3v7h-3V10zM12,1L2,6v2h20V6L12,1z"

    private const val PAYMENTS =
        "M19,14V6c0,-1.1 -0.9,-2 -2,-2H3C1.9,4 1,4.9 1,6v8c0,1.1 0.9,2 2,2h14C18.1,16 19,15.1 19,14zM17,14H3V6h14V14zM10,7c-1.66,0 -3,1.34 -3,3s1.34,3 3,3s3,-1.34 3,-3S11.66,7 10,7zM23,7v11c0,1.1 -0.9,2 -2,2H4v-2h17V7H23z"
}
