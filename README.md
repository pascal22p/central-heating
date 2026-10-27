# Central Heating

Central heating control service written in Scala.

The service integrates Google Nest with Zigbee2MQTT devices to automate heating demand, while exposing metrics through Graphite.

## Features

- Google Nest thermostat integration
- Zigbee2MQTT integration for TRVs and temperature sensors
- Heating-demand control based on temperature sensors and TRVs
- Manual heating control
- Graphite metrics
- Web interface
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
| `DB_CACHE_TTL` | Database cache TTL |

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
| `MQTT_TRV_DEVICES` | Configured TRV devices |
| `MQTT_TEMPERATURE_DEVICES` | Configured temperature sensors |

MQTT is disabled by default.

### Heating demand

| Variable | Description |
|---|---|
| `HEATING_DEMAND_IS_ENABLED` | Enables heating-demand control |
| `HEATING_DEMAND_SENSOR_CONTROL_IS_ENABLED` | Enables sensor-based demand |
| `HEATING_DEMAND_TRV_CONTROL_IS_ENABLED` | Enables TRV-based demand |
| `HEATING_DEMAND_ACTIVE_TEMPERATURE` | Active target temperature |
| `HEATING_DEMAND_STANDBY_TEMPERATURE` | Standby target temperature |
| `HEATING_DEMAND_MINIMUM_ON` | Minimum heating-on period |
| `HEATING_DEMAND_MINIMUM_OFF` | Minimum heating-off period |

Defaults:

```text
HEATING_DEMAND_IS_ENABLED=false
HEATING_DEMAND_SENSOR_CONTROL_IS_ENABLED=false
HEATING_DEMAND_TRV_CONTROL_IS_ENABLED=false
HEATING_DEMAND_ACTIVE_TEMPERATURE=21
HEATING_DEMAND_STANDBY_TEMPERATURE=16
HEATING_DEMAND_MINIMUM_ON=10 minutes
HEATING_DEMAND_MINIMUM_OFF=5 minutes
```

### Nest scheduler

| Variable | Description |
|---|---|
| `NEST_SCHEDULER_IS_ENABLED` | Enables the Nest scheduler |
| `NEST_SCHEDULER_START_DELAY` | Initial scheduler delay |
| `NEST_SCHEDULER_INTERVAL` | Scheduler interval |
| `NEST_SCHEDULER_TIMEOUT` | Scheduler operation timeout |

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

## Project layout

```text
app/                         Application code
conf/                        Play configuration and routes
project/                     sbt configuration and dependencies
test/                        Tests
build.sbt                    Build definition
```

## License

See the repository for licensing information.