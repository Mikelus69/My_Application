package de.touchgrass

import de.touchgrass.data.SportApi
import de.touchgrass.data.createSportApi

class AppContainer(baseUrl: String) {
    val api: SportApi = createSportApi(baseUrl)
}
