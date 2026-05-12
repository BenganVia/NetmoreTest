package com.viametrics.seraph.config

interface SensorConfigProvider {
    fun load(): List<SensorConfig>
}