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
	definition (name: "GCal Lock", namespace: "aerojsam", author: "Samuel Jimenez") {        
        capability "Actuator"
        //capability "Lock"
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

/*>> DEVICE SETTINGS: LOCK >>*/
/* USED BY TRIGGER APP. TO ACCESS, USE parent.<setting>. */
Map deviceSettings() {
    return [
        1: [input: [name: "deviceState", type: "enum", title: "Lock Default State", description: "", defaultValue: "unlock", options: ["lock","unlock"]], required: true, submitOnChange: true, parameterSize: 1]
    ]
}
/*<< DEVICE SETTINGS: LOCK <<*/

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

    def currentValue = device.currentLock
    def defaultValue = (settings.lockValue == null) ? parent.getDefaultDeviceState() : settings.lockValue
    def toggleValue = (defaultValue == "lock") ? "unlock":"lock"
	logMsg.push("poll - BEFORE (${new Date()}) - currentValue: ${currentValue} | defaultValue: ${defaultValue} | toggleValue: ${toggleValue} ")
    
    def result = []
    def syncValue
    def item = parent.getNextEvents()
    
    def eventTitle = " "
    def eventLocation = " "
    def eventAllDay = " "
    def eventStartTime = " "
    def eventEndTime = " "
    def eventReservationURL = " "
    def eventLast4Tel = " "
    
    if (item && item.eventTitle) {
        logMsg.push("event found, item: ${item}")
        
        eventTitle = item.eventTitle
        eventLocation = item.eventLocation
        eventAllDay = item.eventAllDay
        eventStartTime = parent.formatDateTime(item.eventStartTime)
        eventEndTime = parent.formatDateTime(item.eventEndTime)
        eventReservationURL = item.eventReservationURL
        eventLast4Tel = item.eventLast4Tel
        
        syncValue = parent.scheduleEvent(item.scheduleStartTime, item.scheduleEndTime, [defaultValue: defaultValue, currentValue: currentValue, toggleValue: toggleValue])
    } else {
        logMsg.push("no events found, turning ${defaultValue} lock")
        syncValue = defaultValue
    }
    
    result << sendEvent(name: "eventTitle", value: eventTitle )
    result << sendEvent(name: "eventLocation", value: eventLocation )
    result << sendEvent(name: "eventAllDay", value: eventAllDay )
    result << sendEvent(name: "eventStartTime", value: eventStartTime )
    result << sendEvent(name: "eventEndTime", value: eventEndTime )
    result << sendEvent(name: "lastUpdated", value: parent.formatDateTime(new Date()), displayed: false)
    
    syncChildDevices(syncValue)
    logDebug("${logMsg}")
    return result
}

def clearEventCache() {
    parent.clearEventCache()
}

def engage() {
	logDebug("engage() - Engage/lock")
    sendEvent(name: "lock", "locked")
	parent.syncChildDevices("engage")
}

def disengage() {
	logDebug("disengage() - Disengage/unlocked")
    sendEvent(name: "lock", "unlocked")
	parent.syncChildDevices("disengage")
}

Map nativeMethods() {
    return [
        engage: [lock, []],
		disengage: [unlock, []]
    ]
}

def syncChildDevices(value) {
    parent.syncChildDevices(parent.convertToState(value))
}

private logDebug(msg) {
    if (isDebugEnabled != null && isDebugEnabled != false) {
        if (msg instanceof List && msg.size() > 0) {
            msg = msg.join(", ");
        }
        log.debug "$msg"
    }
}
