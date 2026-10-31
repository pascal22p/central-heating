# Central Heating

Central heating control service written in Scala.

The service integrates Google Nest with Zigbee2MQTT devices to automate heating demand, while exposing metrics through Graphite.

## Features

- Google Nest thermostat integration
- Zigbee2MQTT integration for TRVs and temperature sensors
- Remote temperature sensor to TRV synchronisation
- Heating-demand control based on temperature sensors and TRVs
- Manual heating override and Homebridge virtual switch API
- Graphite metrics
- Web interface and administration dashboard
- MariaDB persistence
- Configurable Nest scheduling
- Docker images for `amd64` and `arm64`

## Stack

- Scala 3
- Play Framework
- sbt
- MariaDB / Anorm
- Pekko MQTT
- Google Nest API
- Zigbee2MQTT
- Graphite
- Docker

## Configuration

Configuration is supplied through environment variables.

### Application

| Variable | Description |
|---|---|
| `APP_SECRET` | Play application secret |
| `IS_SESSION_SECURE` | Whether sessions use secure cookies |
| `PLAY_FILTERS_HOSTS` | Allowed hosts |
| `PROTOCOL` | Application protocol |
| `API_BEARER_TOKEN` | API authentication token |

The application listens on port `9245`.

### Database

| Variable | Description |
|---|---|
| `DB_URL` | MariaDB JDBC URL |
| `DB_USER` | Database user |
| `DB_PASSWORD` | Database password |
| `DB_CACHE_QUERIES_TTL` | Database queries cache TTL (minutes) |

### Nest

| Variable | Description |
|---|---|
| `NEST_CLIENT_ID` | Nest OAuth client ID |
| `NEST_CLIENT_SECRET` | Nest OAuth client secret |
| `NEST_PROJECT_ID` | Nest project ID |
| `NEST_DEVICE_ID` | Nest device ID |
| `NEST_REDIRECT_URI` | OAuth redirect URI |

### MQTT

| Variable | Description |
|---|---|
| `MQTT_IS_ENABLED` | Enables MQTT integration |
| `MQTT_BROKER_URL` | MQTT broker URL |
| `MQTT_TRV_DEVICES` | Configured TRV devices (comma-separated topic names) |
| `MQTT_TEMPERATURE_DEVICES` | Configured temperature sensors (comma-separated topic names) |
| `TEMPERATURE_SENSOR_MAPPING` | Mapping of external temperature sensor to TRV (e.g. `sensor1:trv1,sensor2:trv2`) |

MQTT is disabled by default.

### Heating demand

| Variable | Description |
|---|---|
| `HEATING_DEMAND_IS_ENABLED` | Enables heating-demand control |
| `HEATING_SENSOR_CONTROL_IS_ENABLED` | Enables sensor-based demand |
| `HEATING_TRV_CONTROL_IS_ENABLED` | Enables TRV-based demand |
| `HEATING_DEMAND_ACTIVE_TEMPERATURE_CELSIUS` | Active target temperature (°C) |
| `HEATING_DEMAND_STANDBY_TEMPERATURE_CELSIUS` | Standby target temperature (°C) |
| `HEATING_DEMAND_MINIMUM_ON` | Minimum heating-on period |
| `HEATING_DEMAND_MINIMUM_OFF` | Minimum heating-off period |

Defaults:

```text
HEATING_DEMAND_IS_ENABLED=false
HEATING_SENSOR_CONTROL_IS_ENABLED=false
HEATING_TRV_CONTROL_IS_ENABLED=false
HEATING_DEMAND_ACTIVE_TEMPERATURE_CELSIUS=21.0
HEATING_DEMAND_STANDBY_TEMPERATURE_CELSIUS=16.0
HEATING_DEMAND_MINIMUM_ON=10 minutes
HEATING_DEMAND_MINIMUM_OFF=5 minutes
```

### Nest scheduler

| Variable | Description |
|---|---|
| `NEST_SCHEDULER_IS_ENABLED` | Enables the Nest scheduler |
| `NEST_SCHEDULER_START_DELAY_IN_SECONDS` | Initial scheduler delay (seconds) |
| `NEST_SCHEDULER_INTERVAL_IN_SECONDS` | Scheduler interval (seconds) |
| `NEST_SCHEDULER_TIMEOUT_IN_SECONDS` | Scheduler operation timeout (seconds) |

### Graphite

| Variable | Description |
|---|---|
| `GRAPHITE_HOST` | Graphite host |
| `GRAPHITE_PORT` | Graphite port |
| `GRAPHITE_PREFIX` | Metric prefix |

## Development

```bash
sbt run
```

The service runs on port `9245`.

Run the tests with:

```bash
sbt test
```

Build the Docker image locally with:

```bash
sbt Docker / publishLocal
```

## Docker

The build produces multi-platform images for:

- `linux/amd64`
- `linux/arm64`

Release images use `v<version>` and `latest` tags.

Snapshot images use `v<version>-snapshot-<shortsha>` and `latest-snapshot`.

## License

See the repository for licensing information.