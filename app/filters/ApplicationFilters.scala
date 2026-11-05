package filters

import javax.inject.Inject
import play.http.DefaultHttpFilters

class ApplicationFilters @Inject() (
    requestAttrFilter: RequestAttrFilter,
    sessionFilter: SessionFilter,
    accessLogFilter: AccessLogFilter
) extends DefaultHttpFilters(
      requestAttrFilter,
      sessionFilter,
      accessLogFilter
    )
