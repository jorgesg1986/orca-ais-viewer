package com.orca.ais.viewer.data

import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory

object Migrations {

  private val logger = LoggerFactory.getLogger(classOf[Migrations.type])

    def migrate(url: String, user: String, password: String): Unit = {
      logger.info("Checking for database migrations")



      val flyway = Flyway.configure()
        .dataSource(url, user, password)
        .load()

      flyway.migrate()

      logger.info("Database is up-to-date")
    }
}
