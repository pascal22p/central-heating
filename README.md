# central-heating

Central heating and climate management service built with Scala and the Play Framework. The application integrates Google Nest thermostats, Zigbee-based Thermostatic Radiator Valves (TRVs) via MQTT, and time-series metrics forwarding to Graphite.

---

## Table of Contents

- [Overview](#overview)
- [Tech Stack](#tech-stack)
- [Requirements](#requirements)
- [Configuration & Environment Variables](#configuration--environment-variables)
- [License](#license)

---

## Overview

`central-heating` is a microservice designed to centralize and automate domestic heating controls:
- **Nest Thermostat Integration**: Communicates with the Google Smart Device Management (SDM) API to monitor ambient temperature, humidity, operational modes, and setpoints, as well as adjust temperature settings.
- **Zigbee TRV Monitoring**: Ingests real-time MQTT telemetry from Zigbee2MQTT for radiator valves (TRVs).
- **Time-Series Metrics**: Pushes thermostat and heating telemetry directly to Graphite (Carbon) for Grafana dashboards.
- **Web UI & Admin Management**: Built using Twirl templates with the HMRC Design System (`play-frontend-hmrc`), featuring session-based authentication and role-based access control.

---

## Tech Stack

- **Language**: [Scala](https://www.scala-lang.org/) `3.9.0`
- **Web Framework**: [Play Framework](https://www.playframework.com/) `3.0.12`
- **Asynchronous & Streaming Runtime**: [Apache Pekko](https://pekko.apache.org/) (Actors, Streams, Pekko Connectors MQTT)
- **UI / Frontend**: Play Twirl, HMRC Play Frontend (`play-frontend-hmrc-play-30` v13.15.0)
- **Database / Data Access**: MariaDB Java Client (`mariadb-java-client` v3.5.10) with [Anorm](https://playframework.github.io/anorm/) `3.1.0`
- **Security & Authentication**: [Password4j](https://github.com/Password4j/password4j) `1.8.4` (Argon2 / BCrypt / PBKDF2)
- **Metrics & Tracing**: Graphite Carbon client (TCP), OpenTelemetry Instrumentation
- **Build Tool & Package Manager**: [sbt](https://www.scala-sbt.org/) `1.13.0`
- **Packaging**: `sbt-native-packager` (Docker multi-arch support with Eclipse Temurin 25)
- **Testing**: ScalaTest, `scalatestplus-play`, Mockito, WireMock, Jsoup, Scoverage

---

## Requirements

- **Java Development Kit (JDK)**: JDK 21+ (JDK 25 recommended, base docker image uses `eclipse-temurin:25`)
- **sbt**: `1.13.0` or higher
- **MariaDB / MySQL**: `10.6+` or `8.0+`
- **MQTT Broker**: Mosquitto or compatible MQTT broker connected to Zigbee2MQTT
- **Graphite Server**: Carbon receiver on port `2003`
- **Google Cloud Project**: Device Access Console project and OAuth credentials for Nest SDM API

---

## Configuration & Environment Variables

All settings in `conf/application.conf` can be customized via environment variables:

| Environment Variable | Description | Default Value | Config Path |
|---|---|---|---|
| `APP_SECRET` | Play application secret key for cryptographic operations | (Auto-generated/None) | `play.http.secret.key` |
| `IS_SESSION_SECURE` | Set `true` to restrict session cookies to HTTPS | `false` | `play.http.session.secure` |
| `PLAY_FILTERS_HOSTS` | Allowed hostnames for the Host filter | `localhost:9245` | `allowedHost` |
| `PROTOCOL` | Protocol used for absolute URL construction | `http://` | `protocol` |
| `DB_URL` | JDBC database connection URL | `jdbc:mariadb://localhost:3306/central_heating?createDatabaseIfNotExist=true` | `db.default.url` |
| `DB_USER` | Database username | `root` | `db.default.username` |
| `DB_PASSWORD` | Database password | `example` | `db.default.password` |
| `NEST_CLIENT_ID` | Google OAuth 2.0 Client ID for Nest SDM | _None_ | `microservice.services.nest.client-id` |
| `NEST_CLIENT_SECRET` | Google OAuth 2.0 Client Secret | _None_ | `microservice.services.nest.client-secret` |
| `NEST_PROJECT_ID` | Google Cloud Device Access Project ID | _None_ | `microservice.services.nest.project-id` |
| `NEST_DEVICE_ID` | Nest Thermostat Device Identifier | _None_ | `microservice.services.nest.device-id` |
| `NEST_REDIRECT_URI` | Nest OAuth callback redirect URL | `http://localhost:9245/admin/nest/oauth/callback` | `microservice.services.nest.redirect-uri` |
| `GRAPHITE_HOST` | Graphite Carbon server hostname | `localhost` | `microservice.services.graphite.host` |
| `GRAPHITE_PORT` | Graphite Carbon server port | `2003` | `microservice.services.graphite.port` |
| `GRAPHITE_PREFIX` | Prefix prepended to metrics sent to Graphite | `home.heating.nest` | `microservice.services.graphite.prefix` |
| `NEST_SCHEDULER_IS_ENABLED` | Enable scheduled background Nest metric pushes | `false` | `scheduler.nest.isEnabled` |
| `NEST_SCHEDULER_START_DELAY_IN_SECONDS` | Initial delay before starting the Nest scheduler | `20` | `scheduler.nest.startDelayInSeconds` |
| `NEST_SCHEDULER_INTERVAL_IN_SECONDS` | Interval between metric pushes in seconds | `60` | `scheduler.nest.intervalInSeconds` |
| `NEST_SCHEDULER_TIMEOUT_IN_SECONDS` | Timeout duration for metric collection job | `10` | `scheduler.nest.timeoutInSeconds` |
| `RELEASE_BUILD` | Set to `true` during sbt Docker builds for non-snapshot tagging | _None_ | (Used in `build.sbt`) |

---

## License

This project is dedicated to the public domain under [The Unlicense](LICENSE). Free for commercial and non-commercial use.
