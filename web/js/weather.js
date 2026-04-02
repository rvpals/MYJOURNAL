/**
 * Weather module using Open-Meteo API (free, no API key needed).
 * Geocoding for city search, current weather fetch.
 */

const Weather = (() => {
    const GEO_URL = 'https://geocoding-api.open-meteo.com/v1/search';
    const WEATHER_URL = 'https://api.open-meteo.com/v1/forecast';

    // WMO Weather interpretation codes -> descriptions
    const WMO_CODES = {
        0: 'Clear sky',
        1: 'Mainly clear',
        2: 'Partly cloudy',
        3: 'Overcast',
        45: 'Foggy',
        48: 'Depositing rime fog',
        51: 'Light drizzle',
        53: 'Moderate drizzle',
        55: 'Dense drizzle',
        56: 'Light freezing drizzle',
        57: 'Dense freezing drizzle',
        61: 'Slight rain',
        63: 'Moderate rain',
        65: 'Heavy rain',
        66: 'Light freezing rain',
        67: 'Heavy freezing rain',
        71: 'Slight snowfall',
        73: 'Moderate snowfall',
        75: 'Heavy snowfall',
        77: 'Snow grains',
        80: 'Slight rain showers',
        81: 'Moderate rain showers',
        82: 'Violent rain showers',
        85: 'Slight snow showers',
        86: 'Heavy snow showers',
        95: 'Thunderstorm',
        96: 'Thunderstorm with slight hail',
        99: 'Thunderstorm with heavy hail'
    };

    // WMO code -> emoji-like icon text
    const WMO_ICONS = {
        0: 'sunny', 1: 'mostly_sunny', 2: 'partly_cloudy', 3: 'cloudy',
        45: 'fog', 48: 'fog',
        51: 'drizzle', 53: 'drizzle', 55: 'drizzle',
        56: 'freezing_drizzle', 57: 'freezing_drizzle',
        61: 'rain', 63: 'rain', 65: 'heavy_rain',
        66: 'freezing_rain', 67: 'freezing_rain',
        71: 'snow', 73: 'snow', 75: 'heavy_snow',
        77: 'snow', 80: 'showers', 81: 'showers', 82: 'showers',
        85: 'snow_showers', 86: 'snow_showers',
        95: 'thunderstorm', 96: 'thunderstorm', 99: 'thunderstorm'
    };

    /**
     * Search for cities by name. Returns array of { name, country, admin1, lat, lng }.
     */
    async function searchCity(query) {
        if (!query || query.trim().length < 2) return [];
        const url = `${GEO_URL}?name=${encodeURIComponent(query.trim())}&count=5&language=en&format=json`;
        const resp = await fetch(url);
        if (!resp.ok) throw new Error('Geocoding request failed');
        const data = await resp.json();
        if (!data.results) return [];
        return data.results.map(r => ({
            name: r.name,
            country: r.country || '',
            admin1: r.admin1 || '',
            lat: r.latitude,
            lng: r.longitude
        }));
    }

    /**
     * Fetch current weather for given lat/lng.
     * Returns { temp, feelsLike, humidity, windSpeed, description, icon, code }.
     */
    async function fetchCurrent(lat, lng, tempUnit) {
        const unit = tempUnit === 'fahrenheit' ? 'fahrenheit' : 'celsius';
        const url = `${WEATHER_URL}?latitude=${lat}&longitude=${lng}` +
            `&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m` +
            `&temperature_unit=${unit}&wind_speed_unit=mph`;
        const resp = await fetch(url);
        if (!resp.ok) throw new Error('Weather request failed');
        const data = await resp.json();
        const c = data.current;
        const code = c.weather_code;
        return {
            temp: c.temperature_2m,
            feelsLike: c.apparent_temperature,
            humidity: c.relative_humidity_2m,
            windSpeed: c.wind_speed_10m,
            description: WMO_CODES[code] || 'Unknown',
            icon: WMO_ICONS[code] || 'unknown',
            code: code,
            unit: unit === 'fahrenheit' ? 'F' : 'C'
        };
    }

    /**
     * Get saved weather location from settings. Returns { name, lat, lng } or null.
     */
    function getLocation() {
        const settings = DB.getSettings();
        return settings.weatherLocation || null;
    }

    /**
     * Save weather location to settings.
     */
    function setLocation(location) {
        DB.setSettings({ weatherLocation: location });
    }

    /**
     * Get saved temperature unit preference. Returns 'celsius' or 'fahrenheit'.
     */
    function getTempUnit() {
        const settings = DB.getSettings();
        return settings.weatherTempUnit || 'fahrenheit';
    }

    /**
     * Save temperature unit preference.
     */
    function setTempUnit(unit) {
        DB.setSettings({ weatherTempUnit: unit });
    }

    /**
     * Format weather data for display.
     */
    function formatWeather(w) {
        if (!w) return '';
        return `${w.description}, ${w.temp}°${w.unit} (feels ${w.feelsLike}°${w.unit}), ` +
            `Humidity ${w.humidity}%, Wind ${w.windSpeed} mph`;
    }

    return {
        searchCity,
        fetchCurrent,
        getLocation,
        setLocation,
        getTempUnit,
        setTempUnit,
        formatWeather
    };
})();
