package services

import java.time.{ Clock, Instant, LocalTime, ZoneOffset }
import scala.concurrent.{ ExecutionContext, Future }

import uk.gov.hmrc.http.HeaderCarrier

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*

import cats.data.OptionT
import config.AppConfig
import models.{ HeatingSchedule, HeatingScheduleTransition }
import queries.{ DevicesQueries, ScheduleQueries }
import testUtils.BaseSpec

class DevicesServiceSpec extends BaseSpec {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val hc: HeaderCarrier    = HeaderCarrier()

  private lazy val mockDevicesQueries       = mock[DevicesQueries]
  private lazy val mockAppConfig            = mock[AppConfig]
  private lazy val mockMqttPublisherService = mock[MqttPublisherService]
  private lazy val mockScheduleQueries      = mock[ScheduleQueries]

  private val trvTopic         = "trv/living-room"
  private val temperatureTopic = "temperature/living-room"

  private val standbyTemperature = 10.0

  private def fixedClock(dateTime: String): Clock =
    Clock.fixed(
      Instant.parse(dateTime),
      ZoneOffset.UTC
    )

  private val mondayClock   = fixedClock("2026-11-16T10:00:00Z") // Monday
  private val saturdayClock = fixedClock("2026-11-21T10:00:00Z") // Saturday

  private def service(clock: Clock = mondayClock) =
    new DevicesService(
      mockDevicesQueries,
      mockAppConfig,
      mockMqttPublisherService,
      mockScheduleQueries,
      clock
    )

  private def transition(hour: Int, minute: Int, temperature: Double) =
    HeatingScheduleTransition(
      start = LocalTime.of(hour, minute),
      temperature = temperature
    )

  private def schedule(weekdays: Seq[HeatingScheduleTransition], weekend: Seq[HeatingScheduleTransition]) =
    HeatingSchedule(
      description = "Test schedule",
      weekdays = weekdays,
      weekend = weekend
    )

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(
      mockDevicesQueries,
      mockAppConfig,
      mockMqttPublisherService,
      mockScheduleQueries
    )

    when(mockAppConfig.trvDevices)
      .thenReturn(Set(trvTopic))

    when(mockAppConfig.temperatureDevices)
      .thenReturn(Set(temperatureTopic))

    when(mockAppConfig.heatingDemandStandbyTemperatureCelsius)
      .thenReturn(standbyTemperature)

    ()
  }

  "DevicesService.getTrvs" should {

    "return the existing TRVs without requesting their state" in {
      val trv = fakeTrv(name = trvTopic)

      when(mockDevicesQueries.getTrvs)
        .thenReturn(Future.successful(Seq(trv)))

      service().getTrvs.futureValue mustBe Seq(trv)

      verify(mockDevicesQueries).getTrvs
      verifyNoInteractions(mockMqttPublisherService)
    }

    "return an empty sequence when no TRVs are configured or stored" in {
      when(mockAppConfig.trvDevices)
        .thenReturn(Set.empty)

      when(mockDevicesQueries.getTrvs)
        .thenReturn(Future.successful(Seq.empty))

      service().getTrvs.futureValue mustBe empty

      verify(mockDevicesQueries).getTrvs
      verifyNoInteractions(mockMqttPublisherService)
    }

    "fail when requesting the state of a missing TRV fails" in {
      val exception = new RuntimeException("MQTT request failed")

      when(mockDevicesQueries.getTrvs)
        .thenReturn(Future.successful(Seq.empty))

      when(mockMqttPublisherService.requestTrvState(trvTopic))
        .thenReturn(Future.failed(exception))

      service().getTrvs.failed.futureValue mustBe exception

      verify(mockMqttPublisherService).requestTrvState(trvTopic)
      verify(mockDevicesQueries, never()).getTrv(any[String])
    }
  }

  "DevicesService.getTemperatureSensors" should {

    "return the existing temperature sensors without requesting their state" in {
      val sensor = fakeTemperatureSensor(name = temperatureTopic)

      when(mockDevicesQueries.getTemperatureSensors)
        .thenReturn(Future.successful(Seq(sensor)))

      service().getTemperatureSensors.futureValue mustBe Seq(sensor)

      verify(mockDevicesQueries).getTemperatureSensors
      verifyNoInteractions(mockMqttPublisherService)
    }

    "return an empty sequence when no sensors are configured or stored" in {
      when(mockAppConfig.temperatureDevices)
        .thenReturn(Set.empty)

      when(mockDevicesQueries.getTemperatureSensors)
        .thenReturn(Future.successful(Seq.empty))

      service().getTemperatureSensors.futureValue mustBe empty

      verify(mockDevicesQueries).getTemperatureSensors
      verifyNoInteractions(mockMqttPublisherService)
    }

    "fail when requesting the state of a missing temperature sensor fails" in {
      val exception = new RuntimeException("MQTT request failed")

      when(mockDevicesQueries.getTemperatureSensors)
        .thenReturn(Future.successful(Seq.empty))

      when(mockMqttPublisherService.requestTemperatureState(temperatureTopic))
        .thenReturn(Future.failed(exception))

      service().getTemperatureSensors.failed.futureValue mustBe exception

      verify(mockMqttPublisherService).requestTemperatureState(temperatureTopic)
      verify(mockDevicesQueries, never()).getTemperatureSensor(any[String])
    }
  }

  "DevicesService.targetTemperature" should {

    "return the standby temperature when no schedule is found" in {
      when(mockScheduleQueries.getScheduleByTopic(trvTopic, 1))
        .thenReturn(OptionT.none[Future, HeatingSchedule])

      service().targetTemperature(trvTopic, 1).futureValue mustBe standbyTemperature

      verify(mockScheduleQueries).getScheduleByTopic(trvTopic, 1)
    }

    "use the weekday schedule on Monday" in {
      val heatingSchedule = schedule(
        weekdays = Seq(
          transition(6, 0, 18.0),
          transition(9, 0, 20.0),
          transition(12, 0, 22.0)
        ),
        weekend = Seq(
          transition(6, 0, 17.0),
          transition(9, 0, 18.0)
        )
      )

      when(mockScheduleQueries.getScheduleByTopic(trvTopic, 1))
        .thenReturn(OptionT.pure[Future](heatingSchedule))

      service(mondayClock).targetTemperature(trvTopic, 1).futureValue mustBe 20.0
    }

    "use the weekend schedule on Saturday" in {
      val heatingSchedule = schedule(
        weekdays = Seq(
          transition(6, 0, 18.0),
          transition(9, 0, 20.0)
        ),
        weekend = Seq(
          transition(6, 0, 17.0),
          transition(9, 0, 19.0),
          transition(12, 0, 21.0)
        )
      )

      when(mockScheduleQueries.getScheduleByTopic(trvTopic, 1))
        .thenReturn(OptionT.pure[Future](heatingSchedule))

      service(saturdayClock).targetTemperature(trvTopic, 1).futureValue mustBe 19.0
    }

    "select the latest transition before the current time" in {
      val heatingSchedule = schedule(
        weekdays = Seq(
          transition(6, 0, 18.0),
          transition(8, 0, 19.0),
          transition(9, 30, 20.0),
          transition(11, 0, 22.0)
        ),
        weekend = Seq.empty
      )

      when(mockScheduleQueries.getScheduleByTopic(trvTopic, 1))
        .thenReturn(OptionT.pure[Future](heatingSchedule))

      service(mondayClock).targetTemperature(trvTopic, 1).futureValue mustBe 20.0
    }

    "ignore transitions later than the current time" in {
      val heatingSchedule = schedule(
        weekdays = Seq(
          transition(6, 0, 18.0),
          transition(11, 0, 22.0),
          transition(12, 0, 23.0)
        ),
        weekend = Seq.empty
      )

      when(mockScheduleQueries.getScheduleByTopic(trvTopic, 1))
        .thenReturn(OptionT.pure[Future](heatingSchedule))

      service(mondayClock).targetTemperature(trvTopic, 1).futureValue mustBe 18.0
    }
  }
}
