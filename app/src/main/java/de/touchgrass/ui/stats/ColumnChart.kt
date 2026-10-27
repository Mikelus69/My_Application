package de.touchgrass.ui.stats

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.ColumnCartesianLayerModel

@Composable
fun ColumnChart(values: List<Int>, labels: List<String>, modifier: Modifier = Modifier) {
    val model = remember(values) { CartesianChartModel(ColumnCartesianLayerModel.build { series(values) }) }
    val step = axisStep(values.maxOrNull() ?: 0)
    val itemPlacer = remember(step) { VerticalAxis.ItemPlacer.step({ step }) }
    val labelFormatter = remember(labels) { CartesianValueFormatter { _, x, _ -> labels.getOrElse(x.toInt()) { "" } } }
    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(itemPlacer = itemPlacer),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = labelFormatter),
            ),
            model = model,
            modifier = modifier.fillMaxWidth().height(200.dp),
        )
    }
}
