package models.mqtt

enum TemperatureSensorSelection derives CanEqual {
  case Internal, Remote, RemoteOffline
}
