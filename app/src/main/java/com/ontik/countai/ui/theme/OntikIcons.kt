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
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(5f, 12f)
            lineTo(19f, 12f)
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
            verticalLineTo(19f)
            close()
            moveTo(14f, 5f)
            verticalLineTo(19f)
            horizontalLineTo(18f)
            verticalLineTo(5f)
            horizontalLineTo(14f)
            close()
        }
    }.build()

    val Crop: ImageVector = ImageVector.Builder(
        name = "Crop",
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
            moveTo(4f, 9f)
            verticalLineTo(5f)
            horizontalLineTo(8f)
            moveTo(16f, 5f)
            horizontalLineTo(20f)
            verticalLineTo(9f)
            moveTo(20f, 15f)
            verticalLineTo(19f)
            horizontalLineTo(16f)
            moveTo(8f, 19f)
            horizontalLineTo(4f)
            verticalLineTo(15f)
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
            horizontalLineTo(7f)
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
            lineTo(12.5f, 7f)
            moveTo(10f, 13f)
            verticalLineTo(22f)
            lineTo(14f, 16.5f)
        }
    }.build()

    val Camera: ImageVector = ImageVector.Builder(
        name = "Camera",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(23f, 19f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21f, 21f)
            horizontalLineTo(3f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1f, 19f)
            verticalLineTo(8f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 6f)
            horizontalLineTo(7f)
            lineTo(9f, 3f)
            horizontalLineTo(15f)
            lineTo(17f, 6f)
            horizontalLineTo(21f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 23f, 8f)
            close()
            moveTo(12f, 17f)
            arcTo(4f, 4f, 0f, isMoreThanHalf = true, isPositiveArc = false, 12f, 9f)
            arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 17f)
            close()
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
            stroke = SolidColor(Color.Black),
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

    val PhotoLibrary: ImageVector = ImageVector.Builder(
        name = "PhotoLibrary",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 5f)
            horizontalLineTo(21f)
            verticalLineTo(19f)
            horizontalLineTo(3f)
            close()
            moveTo(8.5f, 10f)
            arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 8.5f, 7f)
            arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.5f, 10f)
            close()
            moveTo(21f, 15f)
            lineTo(16f, 10f)
            lineTo(5f, 19f)
        }
    }.build()

    val Table: ImageVector = ImageVector.Builder(
        name = "Table",
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
            moveTo(3f, 4f)
            horizontalLineTo(21f)
            verticalLineTo(20f)
            horizontalLineTo(3f)
            close()
            moveTo(3f, 10f)
            horizontalLineTo(21f)
            moveTo(10f, 4f)
            verticalLineTo(20f)
        }
    }.build()

    val Document: ImageVector = ImageVector.Builder(
        name = "Document",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(14f, 2f)
            horizontalLineTo(6f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4f, 4f)
            verticalLineTo(20f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 6f, 22f)
            horizontalLineTo(18f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 20f, 20f)
            verticalLineTo(8f)
            lineTo(14f, 2f)
            close()
            moveTo(14f, 2f)
            verticalLineTo(8f)
            horizontalLineTo(20f)
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

    val FileUpload: ImageVector = ImageVector.Builder(
        name = "FileUpload",
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
            moveTo(4f, 17f)
            verticalLineTo(19f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 6f, 21f)
            horizontalLineTo(18f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 20f, 19f)
            verticalLineTo(17f)
            moveTo(12f, 15f)
            verticalLineTo(3f)
            moveTo(7f, 8f)
            lineTo(12f, 3f)
            lineTo(17f, 8f)
        }
    }.build()
}
