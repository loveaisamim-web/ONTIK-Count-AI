package com.ontik.countai.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object OntikIcons {
    val Minus: ImageVector = ImageVector.Builder(
        name = "Minus",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    }.build()

    val Undo: ImageVector = ImageVector.Builder(
        name = "Undo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9f, 14f)
            lineTo(4f, 9f)
            lineTo(9f, 4f)
            moveTo(4f, 9f)
            horizontalLineTo(15f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 14f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 15f, 19f)
            horizontalLineTo(11f)
        }
    }.build()

    val Pause: ImageVector = ImageVector.Builder(
        name = "Pause",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(6f, 19f)
            horizontalLineTo(10f)
            verticalLineTo(5f)
            horizontalLineTo(6f)
            close()
            moveTo(14f, 5f)
            verticalLineTo(19f)
            horizontalLineTo(18f)
            verticalLineTo(5f)
            close()
        }
    }.build()

    val FlashOn: ImageVector = ImageVector.Builder(
        name = "FlashOn",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(7f, 2f)
            verticalLineTo(13f)
            horizontalLineTo(10f)
            verticalLineTo(22f)
            lineTo(17f, 10f)
            horizontalLineTo(13f)
            lineTo(17f, 2f)
            close()
        }
    }.build()

    val FlashOff: ImageVector = ImageVector.Builder(
        name = "FlashOff",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(3f, 3f)
            lineTo(21f, 21f)
            moveTo(17f, 10f)
            lineTo(10f, 10f)
            lineTo(7f, 2f)
            horizontalLineTo(15f)
            moveTo(10f, 13f)
            verticalLineTo(22f)
            lineTo(14f, 16.5f)
        }
    }.build()

    val History: ImageVector = ImageVector.Builder(
        name = "History",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 8f)
            verticalLineTo(12f)
            lineTo(15f, 15f)
            moveTo(3.05f, 11f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4.22f, 16.5f)
            moveTo(3f, 6f)
            verticalLineTo(11f)
            horizontalLineTo(8f)
        }
    }.build()

    val CalibrateTarget: ImageVector = ImageVector.Builder(
        name = "CalibrateTarget",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 2f)
            verticalLineTo(6f)
            moveTo(12f, 18f)
            verticalLineTo(22f)
            moveTo(2f, 12f)
            horizontalLineTo(6f)
            moveTo(18f, 12f)
            horizontalLineTo(22f)
            moveTo(12f, 17f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 12f, 7f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 17f)
            close()
        }
    }.build()

    val BookmarkCheck: ImageVector = ImageVector.Builder(
        name = "BookmarkCheck",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(19f, 21f)
            lineTo(12f, 16f)
            lineTo(5f, 21f)
            verticalLineTo(5f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7f, 3f)
            horizontalLineTo(17f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19f, 5f)
            close()
            moveTo(9f, 10f)
            lineTo(11f, 12f)
            lineTo(15f, 8f)
        }
    }.build()

    val BugMask: ImageVector = ImageVector.Builder(
        name = "BugMask",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(4f, 4f)
            horizontalLineTo(20f)
            verticalLineTo(20f)
            horizontalLineTo(4f)
            close()
            moveTo(4f, 12f)
            horizontalLineTo(20f)
            moveTo(12f, 4f)
            verticalLineTo(20f)
        }
    }.build()

    val ScienceTest: ImageVector = ImageVector.Builder(
        name = "ScienceTest",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9f, 3f)
            horizontalLineTo(15f)
            moveTo(10f, 3f)
            verticalLineTo(9f)
            lineTo(5f, 19f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 7f, 21f)
            horizontalLineTo(17f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 19f, 19f)
            lineTo(14f, 9f)
            verticalLineTo(3f)
        }
    }.build()
}
