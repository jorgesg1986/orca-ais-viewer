package com.orca.ais.viewer.data

import com.github.tminglei.slickpg.{ExPostgresProfile, PgPostGISSupport}

trait OrcaPostgresProfile extends ExPostgresProfile with PgPostGISSupport {

  override protected def computeCapabilities: Set[slick.basic.Capability] =
    super.computeCapabilities + slick.jdbc.JdbcCapabilities.insertOrUpdate

  override val api: MyAPI = new MyAPI {}
  val plainAPI = new MyAPI with PostGISPlainImplicits

  trait MyAPI extends ExtPostgresAPI with PostGISImplicits with PostGISAssistants


}

object OrcaPostgresProfile extends OrcaPostgresProfile