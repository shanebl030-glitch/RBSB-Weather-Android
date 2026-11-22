// Getting the weather: Open-Meteo first, MET Norway if it fails; the last forecast for each
// place is saved so the app shows something useful offline.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import org.json.JSONObject
import java.io.File

/** A weather service the repository can ask. */
interface Provider {
    val name: String
    fun forecast(place: Place): Forecast
}

class OpenMeteoProvider(private val api: OpenMeteo) : Provider {
    override val name = OpenMeteo.NAME
    override fun forecast(place: Place) = api.forecast(place)
}

class MetNorwayProvider(private val api: MetNorway) : Provider {
    override val name = MetNorway.NAME
    override fun forecast(place: Place) = api.forecast(place)
}

class WeatherRepository(
    private val providers: List<Provider>,
    private val cache: ForecastCache?,
) {
    /**
     * The forecast for a place from the first provider that answers. If all fail, the
     * error says what each one said (the caller can still show the saved copy).
     */
    fun fetch(place: Place): Forecast {
        val problems = mutableListOf<String>()
        for (p in providers) {
            try {
                val fc = p.forecast(place).copy(place = place)
                cache?.save(fc)
                return fc
            } catch (e: WeatherException) {
                problems += "${p.name}: ${e.message}"
            } catch (e: Exception) {
                problems += "${p.name}: unexpected problem (${e.javaClass.simpleName})"
            }
        }
        throw WeatherException(
            if (problems.size == 1) problems[0].substringAfter(": ")
            else "Couldn't get the weather. " + problems.joinToString("  ·  "), retryable = true)
    }

    fun cached(place: Place): Forecast? = cache?.load(place)

    fun forget(place: Place) {
        cache?.remove(place)
    }

    fun cleanUp(keep: List<Place>) {
        cache?.cleanOrphans(keep)
    }
}

/** One small JSON file per place in the app's private folder. */
class ForecastCache(private val dir: File) {
    private fun file(place: Place) = File(dir, "forecast-" + place.key.replace(Regex("[^0-9.,-]"), "_") + ".json")

    fun save(fc: Forecast) {
        dir.mkdirs()
        val target = file(fc.place)
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(fc.toJson().toString())
        if (!tmp.renameTo(target)) {               // rename is atomic on the same folder
            target.delete()
            tmp.renameTo(target)
        }
    }

    fun load(place: Place): Forecast? {
        val f = file(place)
        if (!f.isFile || f.length() > 4 * 1024 * 1024) return null
        return try {
            Forecast.fromJson(JSONObject(f.readText()))?.copy(place = place)
        } catch (e: Exception) {
            null
        }
    }

    fun remove(place: Place) {
        file(place).delete()
    }

    /** Delete saved forecasts of places that are no longer saved. */
    fun cleanOrphans(keep: List<Place>) {
        val names = keep.map { file(it).name }.toSet()
        dir.listFiles()?.forEach { f ->
            if (f.name.startsWith("forecast-") && f.name.endsWith(".json") && f.name !in names) f.delete()
            if (f.name.endsWith(".json.tmp")) f.delete()
        }
    }
}
