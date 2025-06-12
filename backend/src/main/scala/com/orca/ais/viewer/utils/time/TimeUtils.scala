package com.orca.ais.viewer.utils.time

import java.time.Instant

object TimeUtils {

  // timestampUtc has the following format and needs to be converted: "2025-06-09 07:38:57.345752091 +0000 UTC"
  def toInstant(timestampUtc: String): Instant = {
    val timestamp = timestampUtc.split(" ")
    Instant.parse(s"${timestamp(0)}T${timestamp(1)}Z")
  }
}
