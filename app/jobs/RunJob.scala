package jobs

case object RunJob derives CanEqual
given CanEqual[RunJob.type, Any] = CanEqual.derived
given CanEqual[Any, RunJob.type] = CanEqual.derived
