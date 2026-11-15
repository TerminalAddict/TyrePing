package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.feature.main.R

/** Readings own their full measured height; the drawing uses the remaining space. */
@Composable
internal fun MotorcycleLayout(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    tyre: @Composable (Location, Modifier) -> Unit,
    reading: @Composable (Location, Modifier) -> Unit,
    binding: @Composable (Location, Modifier) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val viewportHeight = constraints.maxHeight
        Layout(
            modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(16.dp),
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Front", style = MaterialTheme.typography.titleSmall)
                    reading(Location.Axle.FRONT, Modifier.fillMaxWidth())
                }
                MotorcycleDiagram(tyre, binding)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Rear", style = MaterialTheme.typography.titleSmall)
                    reading(Location.Axle.REAR, Modifier.fillMaxWidth())
                }
            },
        ) { measurables, constraints ->
            val readingConstraints = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
            val front = measurables[0].measure(readingConstraints)
            val rear = measurables[2].measure(readingConstraints)
            val gap = 8.dp.roundToPx()
            val diagramHeight = (viewportHeight - 32.dp.roundToPx() - front.height - rear.height - gap * 2)
                .coerceAtLeast(MIN_MOTORCYCLE_HEIGHT_DP.dp.roundToPx())
            val diagram = measurables[1].measure(Constraints.fixed(constraints.maxWidth, diagramHeight))
            layout(constraints.maxWidth, front.height + diagram.height + rear.height + gap * 2) {
                front.placeRelative(0, 0)
                diagram.placeRelative(0, front.height + gap)
                rear.placeRelative(0, front.height + gap + diagram.height + gap)
            }
        }
    }
}

@Composable
private fun MotorcycleDiagram(
    tyre: @Composable (Location, Modifier) -> Unit,
    binding: @Composable (Location, Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Reserve room for the 48 dp sensor-management buttons at both ends of the drawing.
        val imageHeight = minOf(maxHeight - 48.dp, maxWidth / MOTORCYCLE_ASPECT_RATIO)
        ConstraintLayout(Modifier.fillMaxSize()) {
            val (image, frontTyre, rearTyre, frontBinding, rearBinding) = createRefs()
            Image(
                bitmap = ImageBitmap.imageResource(R.drawable.schema_motorcycle_top_view),
                contentDescription = "Motorcycle viewed from above, front at the top",
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                modifier = Modifier.constrainAs(image) {
                    centerTo(parent)
                    width = Dimension.value(imageHeight * MOTORCYCLE_ASPECT_RATIO)
                    height = Dimension.value(imageHeight)
                },
            )
            tyre(Location.Axle.FRONT, Modifier.constrainAs(frontTyre) {
                centerHorizontallyTo(image)
                top.linkTo(image.top)
                width = Dimension.value(imageHeight * TYRE_WIDTH_FRACTION)
                height = Dimension.value(imageHeight * TYRE_HEIGHT_FRACTION)
            })
            tyre(Location.Axle.REAR, Modifier.constrainAs(rearTyre) {
                centerHorizontallyTo(image)
                bottom.linkTo(image.bottom, imageHeight * REAR_TYRE_INSET_FRACTION)
                width = Dimension.value(imageHeight * TYRE_WIDTH_FRACTION)
                height = Dimension.value(imageHeight * TYRE_HEIGHT_FRACTION)
            })
            binding(Location.Axle.FRONT, Modifier.constrainAs(frontBinding) {
                centerVerticallyTo(frontTyre)
                end.linkTo(frontTyre.start, 8.dp)
            })
            binding(Location.Axle.REAR, Modifier.constrainAs(rearBinding) {
                centerVerticallyTo(rearTyre)
                end.linkTo(rearTyre.start, 8.dp)
            })
        }
    }
}

private const val MIN_MOTORCYCLE_HEIGHT_DP = 200
private const val MOTORCYCLE_ASPECT_RATIO = 208f / 462f
private const val TYRE_HEIGHT_FRACTION = .2f
private const val TYRE_WIDTH_FRACTION = .075f
private const val REAR_TYRE_INSET_FRACTION = .11f
