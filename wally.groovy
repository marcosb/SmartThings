/**
 *  Wally Sensor
 *
 *  Copyright 2021 Marcos B
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */

import physicalgraph.zigbee.clusters.iaszone.ZoneStatus
import physicalgraph.zigbee.zcl.DataType

metadata {
    definition(name: "Wally Sensor", namespace: "marcosb", author: "Marcos Boyington", cstHandler: true) {
        capability "Battery"
        capability "Temperature Measurement"
        capability "Water Sensor"
        capability "Button"
        capability "Relative Humidity Measurement"
        capability "Configuration"
        capability "Contact Sensor"
        capability "Refresh"
    }

    attribute "lastCheckin", "String"
    attribute "lastCheckinDate", "String"
    attribute "lastWet", "String"
    attribute "lastWetDate", "Date"
    attribute "lastOpened", "String"
    attribute "lastOpenedDate", "Date"
    attribute "unknownStatusReport", "String"

    command "enrollResponse"

    fingerprint profileId: "0104", inClusters: "0000,0001,0003,0020,0402,0405,0500,0B05", outClusters: "0003,0006,0019", manufacturer: "Wally", model: "MultiSensor", deviceJoinName: "Wally MultiSensor"

    simulator {
        // TODO: define status and reply messages here
    }

    tiles(scale: 2) {
        multiAttributeTile(name: "water", type: "generic", width: 6, height: 4) {
            tileAttribute("device.water", key: "PRIMARY_CONTROL") {
                attributeState "dry", label: 'Dry', icon: "st.alarm.water.dry", backgroundColor: "#ffffff"
                attributeState "wet", label: 'Wet', icon: "st.alarm.water.wet", backgroundColor: "#00a0dc"
            }
            tileAttribute("device.lastWet", key: "SECONDARY_CONTROL") {
                attributeState "default", label: 'Last Wet: ${currentValue}'
            }
        }
        multiAttributeTile(name: "temperature", type: "generic", width: 6, height: 4) {
            tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
                attributeState("temperature", label: '${currentValue}°',
                        backgroundColors: [
                                [value: 31, color: "#153591"],
                                [value: 44, color: "#1e9cbb"],
                                [value: 59, color: "#90d2a7"],
                                [value: 74, color: "#44b621"],
                                [value: 84, color: "#f1d801"],
                                [value: 95, color: "#d04e00"],
                                [value: 96, color: "#bc2323"]
                        ]
                )
            }
        }
        multiAttributeTile(name: "contact", type: "generic", width: 6, height: 4) {
            tileAttribute("device.contact", key: "PRIMARY_CONTROL") {
                attributeState "open", label: '${name}', icon: "st.contact.contact.open", backgroundColor: "#e86d13"
                attributeState "closed", label: '${name}', icon: "st.contact.contact.closed", backgroundColor: "#00a0dc"
            }
            tileAttribute("device.lastOpened", key: "SECONDARY_CONTROL") {
                attributeState("default", label: 'Last Opened: ${currentValue}')
            }
        }
        valueTile("humidity", "device.humidity", inactiveLabel: false, width: 2, height: 2) {
            state "humidity", label: '${currentValue}%', unit: "%", icon: "https://raw.githubusercontent.com/bspranger/Xiaomi/master/images/XiaomiHumidity.png",
                    backgroundColors: [
                            [value: 0, color: "#FFFCDF"],
                            [value: 4, color: "#FDF789"],
                            [value: 20, color: "#A5CF63"],
                            [value: 23, color: "#6FBD7F"],
                            [value: 56, color: "#4CA98C"],
                            [value: 59, color: "#0072BB"],
                            [value: 76, color: "#085396"]
                    ]
        }
        valueTile("battery", "device.battery", inactiveLabel: false, width: 2, height: 2) {
            state "battery", label: '${currentValue}%', unit: "%", icon: "https://raw.githubusercontent.com/bspranger/Xiaomi/master/images/XiaomiBattery.png",
                    backgroundColors: [
                            [value: 10, color: "#bc2323"],
                            [value: 26, color: "#f1d801"],
                            [value: 51, color: "#44b621"]
                    ]
        }
        valueTile("lastcheckin", "device.lastCheckin", inactiveLabel: false, decoration: "flat", width: 4, height: 1) {
            state "lastcheckin", label: 'Last Event:\n ${currentValue}'
        }
        main("temperature")
        details(["temperature", "battery", "humidity", "lastcheckin"])
    }
    preferences {
        //Date & Time Config
        input description: "", type: "paragraph", element: "paragraph", title: "DATE & CLOCK"
        input name: "dateformat", type: "enum", title: "Set Date Format\n US (MDY) - UK (DMY) - Other (YMD)", description: "Date Format", options: ["US", "UK", "Other"]
        input name: "clockformat", type: "bool", title: "Use 24 hour clock?"
    }
}

def getBATTERY_VOLTAGE_ATTR() { 0x0020 }

def getBATTERY_PERCENT_ATTR() { 0x0021 }

// parse events into attributes
def parse(String description) {
    log.debug "${device.displayName}: Parsing description: ${description}"

    // TODO: Wally MultiSensor sends poll control: "attrId: 0020, result: success, encoding: 20, value: 1f"

    // Determine current time and date in the user-selected date format and clock style
    def now = formatDate()
    def nowDate = new Date(now).getTime()

    // getEvent automatically retrieves temp and humidity in correct unit as integer
    Map map = zigbee.getEvent(description)
    def result = []
    def parsed = false

    // Send message data to appropriate parsing function based on the type of report
    if (map?.name == "temperature") {
        def temp = parseTemperature(description)
        map.value = displayTempInteger ? (int) temp : temp
        map.descriptionText = "${device.displayName} temperature is ${map.value}°${temperatureScale}"
        map.translatable = true
    } else if (map?.name == "humidity") {
        map.value = humidOffset ? (int) map.value + (int) humidOffset : (int) map.value
    } else if (description?.startsWith('zone status')) {
        map = parseIasMessage(description)
        if (map.value == "open") {
            result << createEvent(name: "lastOpened", value: now, displayed: false)
            result << createEvent(name: "lastOpenedDate", value: nowDate, displayed: false)
        }
    } else if (zigbee.isZoneType19(description)) {
        map = getWaterMap(new ZoneStatus(zigbee.translateStatusZoneType19(description) ? 0x1 : 0x0))
        if (map.value == "wet") {
            result << createEvent(name: "lastWet", value: now, displayed: false)
            result << createEvent(name: "lastWetDate", value: nowDate, displayed: false)
        }
    } else {
        Map descMap = zigbee.parseDescriptionAsMap(description)
        log.debug "${device.displayName}: Parsed as map ${descMap}"

        if (descMap?.clusterInt == zigbee.POWER_CONFIGURATION_CLUSTER && descMap.commandInt != 0x07 && descMap.value) {
            log.info "BATT METRICS - attr: ${descMap?.attrInt}, value: ${descMap?.value}, decValue: ${Integer.parseInt(descMap.value, 16)}, currPercent: ${device.currentState("battery")?.value}, device: ${device.getDataValue("manufacturer")} ${device.getDataValue("model")}"
            List<Map> descMaps = collectAttributes(descMap)
            def battMap = descMaps.find { it.attrInt == BATTERY_VOLTAGE_ATTR }

            if (battMap) {
                map = getBatteryResult(Integer.parseInt(descMap.value, 16))
            } else {
                battMap = descMaps.find { it.attrInt == BATTERY_PERCENT_ATTR }
                if (battMap) {
                    map = getBatteryPercentageResult(Integer.parseInt(descMap.value, 16))
                }
            }
        } else if (descMap?.clusterInt == zigbee.TEMPERATURE_MEASUREMENT_CLUSTER && descMap.commandInt == 0x07) {
            if (descMap.data[0] == "00") {
                log.debug "TEMP REPORTING CONFIG RESPONSE: $descMap"
                result << createEvent(name: "checkInterval", value: 60 * 12, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
            } else {
                log.warn "TEMP REPORTING CONFIG FAILED- error code: ${descMap.data[0]}"
                result << createEvent(name: "unknownStatusReport", value: "Temperature map: ${descMap}", displayed: false)
                parsed = false
            }
        } else if (descMap?.clusterInt == zigbee.IAS_ZONE_CLUSTER) {
            def zs = new ZoneStatus(zigbee.convertToInt(descMap.value, 16))
            if (descMap.sourceEndpoint == "02") {
                map = getWaterMap(zs)
                if (map.value == "wet") {
                    result << createEvent(name: "lastWet", value: now, displayed: false)
                    result << createEvent(name: "lastWetDate", value: nowDate, displayed: false)
                }
            } else if (descMap.sourceEndpoint == "01") {
                map = getContactResult(zs)
                if (map.value == "open") {
                    result << createEvent(name: "lastOpened", value: now, displayed: false)
                    result << createEvent(name: "lastOpenedDate", value: nowDate, displayed: false)
                }
            } else {
                log.debug "${device.displayName}: unknown map"
                result << createEvent(name: "unknownStatusReport", value: "IAS Map: ${descMap}", displayed: false)
                parsed = false
            }
        } else {
            log.debug "${device.displayName}: was unable to parse ${description}"
            result << createEvent(name: "unknownStatusReport", value: "Description: ${description}", displayed: false)
            parsed = false
        }
    }

    // Any report - temp, humidity & battery - results in a lastCheckin event and update to Last Checkin tile
    // However, only a non-parseable report results in lastCheckin being displayed in events log
    result << createEvent(name: "lastCheckin", value: now, displayed: !parsed)

    if (map) {
        result << createEvent(map)
        log.debug "${device.displayName}: Parse returned ${map}"
    }

    if (description?.startsWith('enroll request')) {
        List cmds = enrollResponse()
        log.debug "enroll response: ${cmds}"
        result = cmds?.each { result << new physicalgraph.device.HubAction(it) }
    }
    return result
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
    zigbee.readAttribute(zigbee.IAS_ZONE_CLUSTER, zigbee.ATTRIBUTE_IAS_ZONE_STATUS, [destEndpoint: 0x02])
}

def refresh() {
    log.debug "Refreshing Values"
    def refreshCmds = []

    refreshCmds += zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020) +
            zigbee.readAttribute(zigbee.RELATIVE_HUMIDITY_CLUSTER, 0x0000) +
            zigbee.readAttribute(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0x0000) +
            zigbee.readAttribute(0x0003, 0x0000) +
            zigbee.readAttribute(zigbee.IAS_ZONE_CLUSTER, zigbee.ATTRIBUTE_IAS_ZONE_STATUS, [destEndpoint: 0x01]) +
            zigbee.readAttribute(zigbee.IAS_ZONE_CLUSTER, zigbee.ATTRIBUTE_IAS_ZONE_STATUS, [destEndpoint: 0x02])

    log.debug "Refresh Commands: ${refreshCmds}"
    return refreshCmds + enrollResponse()
}

def configure() {
    // Device-Watch allows 2 check-in misses from device + ping (plus 1 min lag time)
    // enrolls with default periodic reporting until newer 5 min interval is confirmed
    sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])

    log.debug "Configuring Reporting"

    def configCmds = []

    // temperature minReportTime 30 seconds, maxReportTime 60 min
    // battery minReport 30 seconds, maxReportTime 6 hrs by default
    // humidity minReportTime 30 seconds, maxReportTime 60 min
    configCmds += zigbee.batteryConfig() +
            zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0x0000, DataType.UINT16, 30, 3600, 100) +
            zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_CLUSTER, 0x0000, DataType.UINT16, 30, 3600, 100) +
            zigbee.configureReporting(0x0003, 0x0000, DataType.UINT16, 30, 3600, null) +
            zigbee.configureReporting(zigbee.IAS_ZONE_CLUSTER, zigbee.ATTRIBUTE_IAS_ZONE_STATUS, DataType.BITMAP16, 30, 3600, null, [destEndpoint: 0x01]) +
            zigbee.configureReporting(zigbee.IAS_ZONE_CLUSTER, zigbee.ATTRIBUTE_IAS_ZONE_STATUS, DataType.BITMAP16, 30, 3600, null, [destEndpoint: 0x02])

    log.debug "Configure Commands: ${configCmds}"
    return refresh() + configCmds
}

def enrollResponse() {
    log.debug "Sending enroll response"
    [] + zigbee.enrollResponse()
}

private Map parseIasMessage(String description) {
    ZoneStatus zs = zigbee.parseZoneStatus(description)

    return getContactResult(zs)
}

// Calculate temperature with 0.1 precision in C or F unit as set by hub location settings
private parseTemperature(String description) {
    def temp = ((description - "temperature: ").trim()) as Float
    def offset = tempOffset ? tempOffset : 0
    temp = (temp > 100) ? (100 - temp) : temp
    temp = (temperatureScale == "F") ? ((temp * 1.8) + 32) + offset : temp + offset
    return temp.round(1)
}

private Map getWaterMap(ZoneStatus zs) {
    def result = [
            name           : 'water',
            descriptionText: 'water contact'
    ]
    if (zs.isAlarm1Set() || zs.isAlarm2Set()) { // detected water
        result.value = "wet"
        result.descriptionText = "${device.displayName} has detected water"
    } else { // did not detect water
        result.value = "dry"
        result.descriptionText = "${device.displayName} is dry"
    }
    return result
}

private Map getContactResult(ZoneStatus zs) {
    def value = zs.isAlarm1Set() || zs.isAlarm2Set()

    log.debug 'Contact Status ${zs}'
    def linkText = getLinkText(device)
    def descriptionText = "${linkText} was ${value ? 'opened' : 'closed'}"
    return [
            name           : 'contact',
            value          : value ? "open" : "closed",
            descriptionText: descriptionText
    ]
}

private Map getBatteryResult(rawValue) {
    log.debug "Battery rawValue = ${rawValue}"
    def linkText = getLinkText(device)

    def result = [:]

    def volts = rawValue / 10

    if (!(rawValue == 0 || rawValue == 255)) {
        result.name = 'battery'
        result.translatable = true
        def minVolts = 2.4
        def maxVolts = 2.7
        // Get the current battery percentage as a multiplier 0 - 1
        def curValVolts = Integer.parseInt(device.currentState("battery")?.value ?: "100") / 100.0
        // Find the corresponding voltage from our range
        curValVolts = curValVolts * (maxVolts - minVolts) + minVolts
        // Round to the nearest 10th of a volt
        curValVolts = Math.round(10 * curValVolts) / 10.0
        // Only update the battery reading if we don't have a last reading,
        // OR we have received the same reading twice in a row
        // OR we don't currently have a battery reading
        // OR the value we just received is at least 2 steps off from the last reported value
        if (state?.lastVolts == null || state?.lastVolts == volts || device.currentState("battery")?.value == null || Math.abs(curValVolts - volts) > 0.1) {
            def pct = (volts - minVolts) / (maxVolts - minVolts)
            def roundedPct = Math.round(pct * 100)
            if (roundedPct <= 0)
                roundedPct = 1
            result.value = Math.min(100, roundedPct)
        } else {
            // Don't update as we want to smooth the battery values, but do report the last battery state for record keeping purposes
            result.value = device.currentState("battery").value
        }
        result.descriptionText = "${device.displayName} battery was ${result.value}%"
        state.lastVolts = volts
    }

    return result
}

private Map getBatteryPercentageResult(rawValue) {
    log.debug "Battery Percentage rawValue = ${rawValue} -> ${rawValue / 2}%"
    def result = [:]

    if (0 <= rawValue && rawValue <= 200) {
        result.name = 'battery'
        result.translatable = true
        result.descriptionText = "{{ device.displayName }} battery was {{ value }}%"
        result.value = Math.round(rawValue / 2)
    }

    return result
}

def formatDate(batteryReset) {
    def correctedTimezone = ""
    def timeString = clockformat ? "HH:mm:ss" : "h:mm:ss aa"

    // If user's hub timezone is not set, display error messages in log and events log, and set timezone to GMT to avoid errors
    if (!(location.timeZone)) {
        correctedTimezone = TimeZone.getTimeZone("GMT")
        log.error "${device.displayName}: Time Zone not set, so GMT was used. Please set up your location in the SmartThings mobile app."
        sendEvent(name: "error", value: "", descriptionText: "ERROR: Time Zone not set, so GMT was used. Please set up your location in the SmartThings mobile app.")
    } else {
        correctedTimezone = location.timeZone
    }
    if (dateformat == "US" || dateformat == "" || dateformat == null) {
        if (batteryReset)
            return new Date().format("MMM dd yyyy", correctedTimezone)
        else
            return new Date().format("EEE MMM dd yyyy ${timeString}", correctedTimezone)
    } else if (dateformat == "UK") {
        if (batteryReset)
            return new Date().format("dd MMM yyyy", correctedTimezone)
        else
            return new Date().format("EEE dd MMM yyyy ${timeString}", correctedTimezone)
    } else {
        if (batteryReset)
            return new Date().format("yyyy MMM dd", correctedTimezone)
        else
            return new Date().format("EEE yyyy MMM dd ${timeString}", correctedTimezone)
    }
}

private List<Map> collectAttributes(Map descMap) {
    List<Map> descMaps = new ArrayList<Map>()

    descMaps.add(descMap)

    if (descMap.additionalAttrs) {
        descMaps.addAll(descMap.additionalAttrs)
    }

    return descMaps
}
