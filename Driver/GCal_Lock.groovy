def driverVersion() { return "2.4.2" }
/**
 *  GCal Lock Driver
 *
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

metadata {
	definition (name: "GCal Lock", namespace: "aerojsam", author: "sammyj") {        
        capability "Actuator"
        capability "Lock"
        capability "Polling"
        capability "Refresh"
        
        attribute "lastUpdated", "string"
        attribute "eventTitle", "string"
        attribute "eventLocation", "string"
        attribute "eventStartTime", "string"
        attribute "eventEndTime", "string"
        attribute "eventAllDay", "bool"
        
        command "clearEventCache"
	}
    
    preferences {
		input name: "isDebugEnabled", type: "bool", title: "Enable debug logging?", defaultValue: false, required: false
        input name: "lockValue", type: "enum", title: "Lock Default Value", required: true, options:["locked","unlocked"]
    }
}

def installed() {
    initialize()
}

def updated() {
	initialize()
}

def initialize() {
    refresh()
}

def parse(String description) {

}

// refresh status
def refresh() {
    poll()
}

def poll() {
    unschedule()
    def logMsg = []

    def nowDateTime = new Date()
    def currentValue = device.currentLock
    def defaultValue = (settings.switchValue == null) ? parent.getDefaultDeviceState() : settings.switchValue
    def toggleValue = (parent.determineState(defaultValue, currentValue, true) == "engage") ? "locked":"unlocked"
    logMsg.push("poll - BEFORE nowDateTime: ${nowDateTime}, currentValue: ${currentValue} AFTER ")
    
    def result = []
    def syncValue
    result << sendEvent(name: "lastUpdated", value: parent.formatDateTime(nowDateTime), displayed: false)
    def item = parent.getNextEvents()
    if (item && item.eventTitle) {
        logMsg.push("event found, item: ${item}")
        
        result << sendEvent(name: "eventTitle", value: item.eventTitle )
        result << sendEvent(name: "eventLocation", value: item.eventLocation )
        result << sendEvent(name: "eventAllDay", value: item.eventAllDay )
        result << sendEvent(name: "eventStartTime", value: parent.formatDateTime(item.eventStartTime) )
        result << sendEvent(name: "eventEndTime", value: parent.formatDateTime(item.eventEndTime) )
        
        syncValue = parent.scheduleEvent(nowDateTime, item.scheduleStartTime, item.scheduleEndTime, defaultValue, toggleValue)
        result << sendEvent(name: "lock", value: syncValue)
    } else {
        logMsg.push("no events found, turning ${defaultValue} lock")
        result << sendEvent(name: "eventTitle", value: " ")
        result << sendEvent(name: "eventLocation", value: " ")
        result << sendEvent(name: "eventAllDay", value: " ")
        result << sendEvent(name: "eventStartTime", value: " ")
        result << sendEvent(name: "eventEndTime", value: " ")
        result << sendEvent(name: "lock", value: defaultValue)
        syncValue = defaultValue
    }
    
    syncChildDevices(syncValue)
    logDebug("${logMsg}")
    return result
}

def clearEventCache() {
    parent.clearEventCache()
}

private logDebug(msg) {
    if (isDebugEnabled != null && isDebugEnabled != false) {
        if (msg instanceof List && msg.size() > 0) {
            msg = msg.join(", ");
        }
        log.debug "$msg"
    }
}
