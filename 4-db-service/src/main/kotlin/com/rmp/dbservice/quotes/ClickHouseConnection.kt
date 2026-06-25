package com.rmp.dbservice.quotes

import com.clickhouse.jdbc.ClickHouseDataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.sql.Connection
import java.util.Properties

/**
 * Owns the ClickHouse JDBC connection (HTTP, compression off — no LZ4 lib needed).
 * Encapsulated in a component, NOT exposed as a `DataSource` bean, so Spring Boot's
 * primary Postgres datasource auto-config is unaffected.
 */
@Component
class ClickHouseConnection(
    @Value("\${CLICKHOUSE_URL:jdbc:clickhouse://localhost:8123/analytics}") url: String,
    @Value("\${CLICKHOUSE_USER:analytics}") user: String,
    @Value("\${CLICKHOUSE_PASSWORD:analytics_dev_pass}") password: String,
) {
    private val ds = ClickHouseDataSource(
        url,
        Properties().apply {
            setProperty("user", user)
            setProperty("password", password)
            setProperty("compress", "0")
        },
    )

    fun open(): Connection = ds.connection
}
