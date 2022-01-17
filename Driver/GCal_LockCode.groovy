def driverVersion() { return "2.4.2" }
/**
 *  GCal LockCode Driver
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

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

metadata {
	definition (name: "GCal Lockcode", namespace: "aerojsam", author: "Samuel Jimenez") {        
        capability "Actuator"
        capability "Lock"
		capability "Lock Codes"
        capability "Polling"
        capability "Refresh"
        
        attribute "lastUpdated", "string"
        attribute "eventTitle", "string"
        attribute "eventLocation", "string"
        attribute "eventStartTime", "string"
        attribute "eventEndTime", "string"
        attribute "code", "string"
        attribute "eventAllDay", "bool"
        
        command "testUnlockWithCode", ["STRING"]
        command "clearEventCache"
	}
    
    preferences {
		input name: "isDebugEnabled", type: "bool", title: "Enable debug logging?", defaultValue: false, required: false
        input name: "lockValue", type: "bool", title: "Lock Default Value [On/Locked, Off/Unlocked]", defaultValue: true
        input name: "optEncrypt", type: "bool", title: "Enable lockCode encryption", defaultValue: false, description: ""
    }
}
String guestCodeName() {
    return "AirBnB Guest Code"
}

/*>> DEVICE SETTINGS: LOCKCODE >>*/
/* USED BY TRIGGER APP. TO ACCESS, USE parent.<setting>. */
Map deviceSettings() {
    return [
        1: [input: [name: "lockCodePosition", type: "number", title: "GCal Lock Position [15 to 20]", description: "[15 - 20]", range: "15..20", defaultValue: "20"], required: true, submitOnChange: true, parameterSize: 1]
    ]
}
/*<< DEVICE SETTINGS: LOCKCODE <<*/


def installed() {
    sendEvent(name:"maxCodes",value:20)
    sendEvent(name:"codeLength",value:4)
    sendEvent(name: "lock", value: settings.lockValue)
    sendEvent(name: "code", value: "")
	
	//add a test lock code
    //setCode(1, "1234", "Hubitat")
    //setCode(2, "4321", "Hubitat2")
    
    initialize()
}

def updated() {
    updateEncryption()
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

    def currentValue = getCodeMap(getLockCodes(), parent.lockCodePosition).code
    def defaultValue = "" // let default lockcode be empty
	def toggleValue
    logMsg.push("poll - BEFORE (${new Date()}) - currentValue: ${currentValue} AFTER ")
    logMsg.push("device settings - lockCodePosition: ${parent.lockCodePosition}")
    
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
		
		// get information from calendar to set def toggleValue
		toggleValue = eventLast4Tel
        
        logMsg.push(">>>>>>>>>> currentValue: ${currentValue} | defaultValue: ${defaultValue} | toggleValue: ${toggleValue} <<<<<<<<<<")
        
        // the toggleValue will always be part of the engage event here
        syncValue = parent.scheduleEvent(item.scheduleStartTime, item.scheduleEndTime, [defaultValue: defaultValue, currentValue: currentValue, toggleValue: toggleValue])
        
    } else {
        logMsg.push("no events found, set lock code to ${defaultValue}")
        syncValue = defaultValue
    }
    
    logMsg.push(">>>>>>>>>> syncValue: ${syncValue} <<<<<<<<<<")
    
    result << sendEvent(name: "eventTitle", value: eventTitle )
    result << sendEvent(name: "eventLocation", value: eventLocation )
    result << sendEvent(name: "eventAllDay", value: eventAllDay )
    result << sendEvent(name: "eventStartTime", value: eventStartTime )
    result << sendEvent(name: "eventEndTime", value: eventEndTime )
    result << sendEvent(name: "lastUpdated", value: parent.formatDateTime(new Date()), displayed: false)
    result << sendEvent(name: "reservationURL", value: eventReservationURL )
    result << sendEvent(name: "code", value: eventLast4Tel )
    
    parent.syncChildDevices(parent.convertToState(syncValue))
    
    logDebug("${logMsg}")
    return result
}

def engage() {
    Integer currentCode = device.currentValue("code")?.toInteger()
    
    logDebug("engage() - Engage/setCode ${currentCode}")
    setCode(parent.lockCodePosition, "${currentCode}", "${guestCodeName()}")
    
    parent.syncChildDevices("engage")
}

def disengage() {
    logDebug("disengage() - Disengage/deleteCode position:${parent.lockCodePosition}")
    deleteCode(parent.lockCodePosition)
    
    parent.syncChildDevices("disengage")
}


Map nativeMethods() {
    Integer currentCode = device.currentValue("code")?.toInteger()
    return [
        engage: ['setCode', [parent.lockCodePosition, "${currentCode}", "${guestCodeName()}"]],
        disengage: ['deleteCode', [parent.lockCodePosition]]
    ]
}

private logDebug(msg) {
    if (isDebugEnabled != null && isDebugEnabled != false) {
        if (msg instanceof List && msg.size() > 0) {
            msg = msg.join(", ");
        }
        log.debug "$msg"
    }
}

/*>> LOCK CODE HELPERS >>*/
Boolean changeIsValid(lockCodes,codeMap,codeNumber,code,name){
    //validate proposed lockCode change
    Boolean result = true
    Integer maxCodeLength = device.currentValue("codeLength")?.toInteger() ?: 4
    Integer maxCodes = device.currentValue("maxCodes")?.toInteger() ?: 20
    Boolean isBadLength = code.size() > maxCodeLength
    Boolean isBadCodeNum = maxCodes < codeNumber
    if (lockCodes) {
        List nameSet = lockCodes.collect{ it.value.name }
        List codeSet = lockCodes.collect{ it.value.code }
        if (codeMap) {
            nameSet = nameSet.findAll{ it != codeMap.name }
            codeSet = codeSet.findAll{ it != codeMap.code }
        }
        Boolean nameInUse = name in nameSet
        Boolean codeInUse = code in codeSet
        if (nameInUse || codeInUse) {
            if (nameInUse) { log.warn "changeIsValid:false, name:${name} is in use:${ lockCodes.find{ it.value.name == "${name}" } }" }
            if (codeInUse) { log.warn "changeIsValid:false, code:${code} is in use:${ lockCodes.find{ it.value.code == "${code}" } }" }
            result = false
        }
    }
    if (isBadLength || isBadCodeNum) {
        if (isBadLength) { log.warn "changeIsValid:false, length of code ${code} does not match codeLength of ${maxCodeLength}" }
        if (isBadCodeNum) { log.warn "changeIsValid:false, codeNumber ${codeNumber} is larger than maxCodes of ${maxCodes}" }
        result = false
    }
    return result
}

Map getCodeMap(lockCodes,codeNumber){
    Map codeMap = [:]
    Map lockCode = lockCodes?."${codeNumber}"
    if (lockCode) {
        codeMap = ["name":"${lockCode.name}", "code":"${lockCode.code}"]
    }
    return codeMap
}

Map getLockCodes() {
    /*
	on a real lock we would fetch these from the response to a userCode report request
	*/
    String lockCodes = device.currentValue("lockCodes")
    Map result = [:]
    if (lockCodes) {
        //decrypt codes if they're encrypted
        if (lockCodes[0] == "{") result = new JsonSlurper().parseText(lockCodes)
        else result = new JsonSlurper().parseText(decrypt(lockCodes))
    }
    return result
}

void getCodes() {
    //no op
}

void updateLockCodes(lockCodes){
    /*
	whenever a code changes we update the lockCodes event
	*/
    logDebug("updateLockCodes: ${lockCodes}")
    String strCodes = JsonOutput.toJson(lockCodes)
    if (optEncrypt) {
        strCodes = encrypt(strCodes)
    }
    sendEvent(name:"lockCodes", value:strCodes, isStateChange:true)
}

void updateEncryption(){
    /*
	resend lockCodes map when the encryption option is changed
	*/
    String lockCodes = device.currentValue("lockCodes") //encrypted or decrypted
    if (lockCodes){
        if (optEncrypt && lockCodes[0] == "{") {	//resend encrypted
            sendEvent(name:"lockCodes",value: encrypt(lockCodes))
        } else if (!optEncrypt && lockCodes[0] != "{") {	//resend decrypted
            sendEvent(name:"lockCodes",value: decrypt(lockCodes))
        }
    }
}
/*<< LOCK CODE HELPERS <<*/

/*>> LOCK CODE CAPABILITY HANDLERS >>*/
void setCode(codeNumber, code, name = null) {
    /*
	on sucess
		name		value								data												notes
		codeChanged	added | changed						[<codeNumber>":["code":"<pinCode>", "name":"<display name for code>"]]	default name to code #<codeNumber>
		lockCodes	JSON map of all lockCode
	*/
 	if (codeNumber == null || codeNumber == 0 || code == null) return

    logDebug("setCode- ${codeNumber}")
	
    if (!name) name = "code #${codeNumber}"

    Map lockCodes = getLockCodes()
    logDebug("lockCodes- ${lockCodes}")
    Map codeMap = getCodeMap(lockCodes,codeNumber)
    if (!changeIsValid(lockCodes,codeMap,codeNumber,code,name)) return
	
   	Map data = [:]
    String value
	
    logDebug("setting code ${codeNumber} to ${code} for lock code name ${name}")

    if (codeMap) {
        if (codeMap.name != name || codeMap.code != code) {
            codeMap = ["name":"${name}", "code":"${code}"]
            lockCodes."${codeNumber}" = codeMap
            data = ["${codeNumber}":codeMap]
            if (optEncrypt) data = encrypt(JsonOutput.toJson(data))
            value = "changed"
        }
    } else {
        codeMap = ["name":"${name}", "code":"${code}"]
        data = ["${codeNumber}":codeMap]
        lockCodes << data
        if (optEncrypt) data = encrypt(JsonOutput.toJson(data))
        value = "added"
    }
    updateLockCodes(lockCodes)
    sendEvent(name:"codeChanged",value:value,data:data, isStateChange: true)
}

void deleteCode(codeNumber) {
    /*
	on sucess
		name		value								data
		codeChanged	deleted								[<codeNumber>":["code":"<pinCode>", "name":"<display name for code>"]]
		lockCodes	[<codeNumber>":["code":"<pinCode>", "name":"<display name for code>"],<codeNumber>":["code":"<pinCode>", "name":"<display name for code>"]]
	*/
    Map codeMap = getCodeMap(lockCodes,"${codeNumber}")
    if (codeMap) {
		Map result = [:]
        //build new lockCode map, exclude deleted code
        lockCodes.each{
            if (it.key != "${codeNumber}"){
                result << it
            }
        }
        updateLockCodes(result)
        Map data =  ["${codeNumber}":codeMap]
        //encrypt lockCode data is requested
        if (optEncrypt) data = encrypt(JsonOutput.toJson(data))
        sendEvent(name:"codeChanged",value:"deleted",data:data, isStateChange: true)
    }
}

void setCodeLength(length){
    /*
	on install/configure/change
		name		value
		codeLength	length
	*/
    String descriptionText = "${device.displayName} codeLength set to ${length}"
    logDebug("${descriptionText}")
    sendEvent(name:"codeLength",value:length,descriptionText:descriptionText)
}
/*<< LOCK CODE CAPABILITY HANDLERS <<*/

/*>> LOCK CODE COMMAND HANDLERS >>*/
def clearEventCache() {
    parent.clearEventCache()
}

void testUnlockWithCode(code = null){
    if (logEnable) log.debug "testUnlockWithCode: ${code}"
    /*
	lockCodes in this context calls the helper function getLockCodes()
	*/
    Object lockCode = lockCodes.find{ it.value.code == "${code}" }
    if (lockCode){
        Map data = ["${lockCode.key}":lockCode.value]
        String descriptionText = "${device.displayName} was unlocked by ${lockCode.value.name}"
        logDebug("${descriptionText}")
        if (optEncrypt) data = encrypt(JsonOutput.toJson(data))
        sendEvent(name:"lock",value:"unlocked",descriptionText: descriptionText, type:"physical",data:data, isStateChange: true)
        sendEvent(name:"lastCodeName", value: lockCode.value.name, descriptionText: descriptionText, isStateChange: true)
    } else {
        logDebug("testUnlockWithCode failed with invalid code")
    }
}

void lock(){
    String descriptionText = "${device.displayName} was locked"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name:"lock",value:"locked",descriptionText: descriptionText, type:"digital")
}

void unlock(){
    /*
    on sucess event
        name	value								data
        lock	unlocked | unlocked with timeout	[<codeNumber>:[code:<pinCode>, name:<display name for code>]]
    */
    String descriptionText = "${device.displayName} was unlocked [digital]"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name:"lock",value:"unlocked",descriptionText: descriptionText, type:"digital")
}

/*<< LOCK CODE COMMAND HANDLERS <<*/
