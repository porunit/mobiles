package com.rmp.dbservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class DbServiceApplication

fun main(args: Array<String>) {
    runApplication<DbServiceApplication>(*args)
}
