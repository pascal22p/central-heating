package filters

import javax.inject.Inject
import play.api.http.EnabledFilters
import play.http.DefaultHttpFilters

class ApplicationFilters @Inject() (
    enabledFilters: EnabledFilters,
    requestAttrFilter: RequestAttrFilter,
    sessionFilter: SessionFilter,
    accessLogFilter: AccessLogFilter
) extends DefaultHttpFilters(
      (Seq(
        requestAttrFilter,
        sessionFilter,
        accessLogFilter
      ) ++ enabledFilters.filters)*
    )
