/**
*  Copyright 2015 SmartThings
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
*  PurpleAir Air Quality Station
*
*  Author: SmartThings
*
*  Date: 2018-07-04
*
*	Updates by Barry A. Burke (storageanarchy@gmail.com)
*	Date: 2017 - 2018
*
*	1.0.00 - Initial Release
*	1.0.01 - Cleanup of descriptionTexts & bug fixes
*	1.0.02 - Fixed typos
*	1.0.03 - More string edits
*	1.0.04 - Updated icons & color handling
*	1.0.05 - Now use BigDecimal for maximum precision
*	1.0.06 - Finalized conversion to BigDecimal
*	1.0.07 - Better error handling
*	1.0.08 - Changed all numeric attributes to "number"
*	1.0.09 - Changed to maintain and display only integer AQI (decimals are distracting)
*	1.0.10 - Fixed room/thing tile display
*	1.0.11 - Handles Inside PurpleAir Sensor (only 1 sensor by design)
*	1.0.12 - Internal cleanup of Inside sensor support, added runEvery3Minutes
*	1.0.13 - Code annotations for hubitat users
*	1.0.14 - Added CAQI calculation for new "Air Quality Sensor" - see https://en.wikipedia.org/wiki/Air_quality_index#CAQI
*	1.1.01 - Added automatic support for both SmartThings and Hubitat
*	1.1.02a- Fix null response handling
*	1.1.03 - Fixed descriptionText:
*   1.1.04 - Fixed incorrect collection of temperature, humidity and pressure where both sensors are not available
*	1.1.05 - Added optional debug logging preference setting
*	1.1.06 - Optimized temp/humidity/pressure updates
*	1.1.07 - Fixed Flagged sensors, added Hidden device support (needs owners's Key)
*	1.1.08 - Added reference adjustments for Temp, Humidity & Pressure
*
*/
import groovy.json.JsonSlurper
import java.math.BigDecimal

def getVersionNum() { return "1.1.08" }
private def getVersionLabel() { return "PurpleAir Air Quality Station, version ${getVersionNum()}" }


// **************************************************************************************************************************
// SmartThings/Hubitat Portability Library (SHPL)
// Copyright (c) 2019, Barry A. Burke (storageanarchy@gmail.com)
//
// The following 3 calls are safe to use anywhere within a Device Handler or Application
//  - these can be called (e.g., if (getPlatform() == 'SmartThings'), or referenced (i.e., if (platform == 'Hubitat') )
//  - performance of the non-native platform is horrendous, so it is best to use these only in the metadata{} section of a
//    Device Handler or Application
//
private String  getPlatform() { (physicalgraph?.device?.HubAction ? 'SmartThings' : 'Hubitat') }	// if (platform == 'SmartThings') ...
private Boolean getIsST()     { (physicalgraph?.device?.HubAction ? true : false) }					// if (isST) ...
private Boolean getIsHE()     { (hubitat?.device?.HubAction ? true : false) }						// if (isHE) ...
//
// The following 3 calls are ONLY for use within the Device Handler or Application runtime
//  - they will throw an error at compile time if used within metadata, usually complaining that "state" is not defined
//  - getHubPlatform() ***MUST*** be called from the installed() method, then use "state.hubPlatform" elsewhere
//  - "if (state.isST)" is more efficient than "if (isSTHub)"
//
private String getHubPlatform() {
    if (state?.hubPlatform == null) {
        state.hubPlatform = getPlatform()						// if (hubPlatform == 'Hubitat') ... or if (state.hubPlatform == 'SmartThings')...
        state.isST = state.hubPlatform.startsWith('S')			// if (state.isST) ...
        state.isHE = state.hubPlatform.startsWith('H')			// if (state.isHE) ...
    }
    return state.hubPlatform
}
private Boolean getIsSTHub() { (state.isST) }					// if (isSTHub) ...
private Boolean getIsHEHub() { (state.isHE) }					// if (isHEHub) ...
//
// **************************************************************************************************************************

// example JSON output from local web interface, Indoor (single sensor):
//
// See https://www2.purpleair.com/community/faq#!hc-what-is-the-difference-between-cf-1-and-cf-atm
// for an explanation of CF1 vs ATM average particle density measures
//
// {
//     "SensorId": "84:f3:eb:8f:fe:d2",
//     "DateTime": "2020/09/11T01:51:06z",
//     "Geo": "PurpleAir-fed2",
//     "Mem": 16688,
//     "memfrag": 18,
//     "memfb": 13720,
//     "memcs": 736,
//     "Id": 34796,
//     "lat": 37.465500,
//     "lon": -122.155899,
//     "Adc": 0.05,
//     "loggingrate": 15,
//     "place": "inside",
//     "version": "6.01",
//     "uptime": 1541058,
//     "rssi": -55,
//     "period": 120,
//     "httpsuccess": 64544,
//     "httpsends": 64634,
//     "hardwareversion": "2.0",
//     "hardwarediscovered": "2.0+BME280+PMSX003-A",
//     "current_temp_f": 92, // temperature, Fahrenheit
//     "current_humidity": 34, // relative humidity
//     "current_dewpoint_f": 60, // dewpoint, Fahrenheit
//     "pressure": 1015.86, // pressure, hPa
//     "p25aqic": "rgb(255,227,0)", // AQI Color
//     "pm2.5_aqi": 80, // AQI
//     "pm1_0_cf_1": 13.28, // PM1.0 µg / m^3 (CF1)
//     "p_0_3_um": 2237.04, // Particles/deciliter >=0.3µm
//     "pm2_5_cf_1": 25.74, // PM2.5 µg / m^3 (CF1)
//     "p_0_5_um": 713.78, // Particles/deciliter >=0.5µm
//     "pm10_0_cf_1": 31.10, // PM10 µg / m^3 (CF1)
//     "p_1_0_um": 191.03, // Particles/deciliter >=1.0µm
//     "pm1_0_atm": 13.28, // PM1.0 µg / m^3 (ATM)
//     "p_2_5_um": 31.16, // Particles/deciliter >=2.5µm
//     "pm2_5_atm": 25.72, // PM2.5 µg / m^3 (ATM)
//     "p_5_0_um": 8.26, // Particles/deciliter >=5.0µm
//     "pm10_0_atm": 31.10, // PM10 µg / m^3 (ATM)
//     "p_10_0_um": 3.45, // Particles/deciliter >=10µm
//     "pa_latency": 295,
//     "response": 201,
//     "response_date": 1599788948,
//     "latency": 393,
//     "key1_response": 200,
//     "key1_response_date": 1599789064,
//     "key1_count": 487042,
//     "ts_latency": 546,
//     "key2_response": 200,
//     "key2_response_date": 1599789065,
//     "key2_count": 491990,
//     "ts_s_latency": 484,
//     "key1_response_b": 200,
//     "key1_response_date_b": 1599789066,
//     "key1_count_b": 497904,
//     "ts_latency_b": 497,
//     "wlstate": "Connected",
//     "status_0": 2,
//     "status_1": 2,
//     "status_2": 2,
//     "status_3": 2,
//     "status_4": 2,
//     "status_5": 2,
//     "status_6": 2,
//     "status_7": 0,
//     "status_8": 2,
//     "status_9": 0,
//     "ssid": "scherly"
// }
//
// Unannotated dual-sensor output
//
// {
//     "SensorId": "68:c6:3a:cc:8:e1",
//     "DateTime": "2020/09/11T01:46:38z",
//     "Geo": "PurpleAir-8e1",
//     "Mem": 20664,
//     "memfrag": 21,
//     "memfb": 16184,
//     "memcs": 832,
//     "Id": 6715,
//     "lat": 37.465500,
//     "lon": -122.155899,
//     "Adc": 0.04,
//     "loggingrate": 15,
//     "place": "outside",
//     "version": "6.01",
//     "uptime": 170209,
//     "rssi": -62,
//     "period": 120,
//     "httpsuccess": 8530,
//     "httpsends": 8550,
//     "hardwareversion": "2.0",
//     "hardwarediscovered": "2.0+BME280+PMSX003-B+PMSX003-A",
//     "current_temp_f": 74,
//     "current_humidity": 57,
//     "current_dewpoint_f": 58,
//     "pressure": 1016.88,
//     "p25aqic_b": "rgb(150,0,72)",
//     "pm2.5_aqi_b": 273,
//     "pm1_0_cf_1_b": 151.50,
//     "p_0_3_um_b": 24309.31,
//     "pm2_5_cf_1_b": 336.67,
//     "p_0_5_um_b": 7659.91,
//     "pm10_0_cf_1_b": 370.74,
//     "p_1_0_um_b": 2498.97,
//     "pm1_0_atm_b": 100.14,
//     "p_2_5_um_b": 314.24,
//     "pm2_5_atm_b": 223.62,
//     "p_5_0_um_b": 40.69,
//     "pm10_0_atm_b": 246.19,
//     "p_10_0_um_b": 16.91,
//     "p25aqic": "rgb(153,0,76)",
//     "pm2.5_aqi": 242,
//     "pm1_0_cf_1": 153.72,
//     "p_0_3_um": 28139.00,
//     "pm2_5_cf_1": 288.76,
//     "p_0_5_um": 7900.54,
//     "pm10_0_cf_1": 307.00,
//     "p_1_0_um": 2058.31,
//     "pm1_0_atm": 101.65,
//     "p_2_5_um": 174.15,
//     "pm2_5_atm": 191.67,
//     "p_5_0_um": 20.67,
//     "pm10_0_atm": 203.83,
//     "p_10_0_um": 11.39,
//     "pa_latency": 295,
//     "response": 201,
//     "response_date": 1599788697,
//     "latency": 371,
//     "key1_response": 200,
//     "key1_response_date": 1599788691,
//     "key1_count": 542919,
//     "ts_latency": 543,
//     "key2_response": 200,
//     "key2_response_date": 1599788692,
//     "key2_count": 542746,
//     "ts_s_latency": 492,
//     "key1_response_b": 200,
//     "key1_response_date_b": 1599788693,
//     "key1_count_b": 542559,
//     "ts_latency_b": 478,
//     "key2_response_b": 200,
//     "key2_response_date_b": 1599788695,
//     "key2_count_b": 542431,
//     "ts_s_latency_b": 481,
//     "wlstate": "Connected",
//     "status_0": 2,
//     "status_1": 2,
//     "status_2": 2,
//     "status_3": 2,
//     "status_4": 2,
//     "status_5": 2,
//     "status_6": 2,
//     "status_7": 0,
//     "status_8": 2,
//     "status_9": 2,
//     "ssid": "scherly"
// }
metadata {
  // TODO remove purpleID references
    definition (name: "PurpleAir Local Air Quality Station", namespace: "arigesher", author: "arigesher",
			    importUrl: "tbd") {
        capability "Temperature Measurement"
        capability "Relative Humidity Measurement"
        capability "Signal Strength"
        capability "Sensor"
        capability "Refresh"
        if (isST) {capability "Air Quality Sensor"} else {attribute "airQuality", "number"}

        attribute "locationName", "string"
        attribute "ID", "string"
        attribute "pressure", "number"
		attribute "airQuality", 'number'
        attribute "airQualityIndex", "string"
        attribute "aqi", "number"				// current AQI
		attribute "aqiDisplay", 'string'
		attribute "aqi10", "number"				// 10 minute average
		attribute "aqi30", "number"				// 30 minute average
		attribute "aqi1", "number"				// 1 hour average
		attribute "aqi6", "number"				// 6 hour average
		attribute "aqi24", "number"				// 24 hour average
		attribute "aqi7", "number"				// 7 day average
		attribute "pm", "number"				// current 2.5 PM (particulate matter)
		attribute "pm10", "number"				// 10 minute average
		attribute "pm30", "number"				// 30 minute average
		attribute "pm1", "number"				// 1 hour average
		attribute "pm6", "number"				// 6 hour average
		attribute "pm24", "number"				// 24 hour average
		attribute "pm7", "number"				// 7 day average
		attribute "rssi", "string"				// Signal Strength attribute (not supporting lqi)
        attribute 'message', 'string'
  		attribute "updated", "string"
        attribute "timestamp", "string"
		attribute "temperatureDisplay", 'string'
		attribute "pressure", 'number'
		attribute "pressureDisplay", 'string'
        command "refresh"
    }

    preferences {
		input(name: "localIp", type: "text", title: (isHE?'<b>':'') + "PurpleAir Station IP" + (isHE?'</b>':''), required: true, displayDuringSetup: true, description: 'Enter the local IP address (usually 192.168.X.X) of PurpleAir Station')
		input(name: "purpleKey", type: "password", title: (isHE?'<b>':'') + "PurpleAir Private Key (optional)" + (isHE?'</b>':''), required: false, displayDuringSetup: true, description: "Enter the Private Key for this Station")
    	input(name: 'updateMins', type: 'enum', description: "Select the update frequency",
        	  title: (isHE?'<b>':'') + "Update frequency (minutes)" + (isHE?'</b>':''), displayDuringSetup: true, defaultValue: '5', options: ['1','3','5','10','15','30'], required: true)
		input "referenceTemp", "decimal", title: (isHE?'<b>':'') + "Reference temperature" + (isHE?'</b>':''), description: "Enter current reference temperature reading", displayDuringSetup: false
		input "referenceRH", "number", title: (isHE?'<b>':'') + "Reference relative humidity" + (isHE?'</b>':''), description: "Enter current reference RH% reading", displayDuringSetup: false
		input "referenceInHg", "decimal", title: (isHE?'<b>':'') + "Reference barometric pressure" + (isHE?'</b>':''), description: "Enter current reference InHg reading", displayDuringSetup: false
		input(name: 'debugOn', type: 'bool', title: (isHE?'<b>':'') + "Enable debug logging?" + (isHE?'</b>':''), displayDuringSetup: true, defaultValue: false)
    }

    tiles(scale: 2) {
        multiAttributeTile(name:"airQualityIndex", type:"generic", width:6, height:4, canChangeIcon: false) {
            tileAttribute("device.airQualityIndex", key: "PRIMARY_CONTROL") {
                attributeState("airQualityIndex", label:'${currentValue}', defaultState: true,
      					backgroundColors: (aqiColors)
      				)
      			}
                  tileAttribute("device.message", key: "SECONDARY_CONTROL" ) {
      				attributeState('default', label: '${currentValue}', defaultState: true, icon: "https://raw.githubusercontent.com/SANdood/PurpleAirStation/master/images/purpleair.png")
      			}
        }
        valueTile('caqi', 'device.CAQI', inactiveLabel: false, width: 1, height: 1, decoration: 'flat', wordWrap: true) {
        	state 'default', label: 'CAQI\n${currentValue}', unit: "CAQI",
            	backgroundColors: (caqiColors)
        }
	      valueTile('aqi', 'device.aqi', inactiveLabel: false, width: 1, height: 1, decoration: 'flat', wordWrap: true) {
        	state 'default', label: 'AQI\n${currentValue}', icon: "https://raw.githubusercontent.com/SANdood/PurpleAirStation/master/images/purpleair-small.png",
            	backgroundColors: (aqiColors)
        }
        valueTile('aqiDisplay', 'device.aqiDisplay', inactiveLabel: false, width: 1, height: 1, decoration: 'flat', wordWrap: true) {
        	state 'default', label: '${currentValue}', icon: "https://raw.githubusercontent.com/SANdood/PurpleAirStation/master/images/purpleair-small.png"
        }
        valueTile('pm', 'device.pm', inactiveLabel: false, width: 1, height: 1, decoration: 'flat', wordWrap: true) {
        	state 'default', label: 'Now\n${currentValue}\nµg/m³'
        }
        valueTile("locationTile", "device.locationName", inactiveLabel: false, width: 3, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label:'${currentValue}'
        }
        standardTile("refresh", "device.weather", inactiveLabel: false, width: 1, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label: "", action: "refresh", icon:"st.secondary.refresh"
        }
        valueTile("pressure", "device.pressureDisplay", inactiveLabel: false, width: 1, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label: '${currentValue}'
        }
        valueTile("rssi", "device.rssi", inactiveLabel: false, width: 1, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label: 'RSSI\n${currentValue}db'
        }
        valueTile("ID", "device.ID", inactiveLabel: false, width: 1, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label: 'ID\n${currentValue}'
        }
        valueTile("temperature", "device.temperatureDisplay", width: 1, height: 1, canChangeIcon: true) {
            state "default", label: '${currentValue}°',
    				backgroundColors:[
    		            [value: 31, color: "#153591"],
    		            [value: 44, color: "#1e9cbb"],
    		            [value: 59, color: "#90d2a7"],
    		            [value: 74, color: "#44b621"],
    		            [value: 84, color: "#f1d801"],
    		            [value: 95, color: "#d04e00"],
    		            [value: 96, color: "#bc2323"]
                	]
        }
        valueTile("humidity", "device.humidity", decoration: "flat", width: 1, height: 1) {
      			state("default", label: '${currentValue}%', unit: "%", defaultState: true, backgroundColors: [ //#d28de0")
                		[value: 10, color: "#00BFFF"],
                    [value: 100, color: "#ff66ff"]
                  ])
      	}
       //main(["aqiDisplay"])
        main(['airQualityIndex'])
        details([	"airQualityIndex", 'temperature', 'humidity',
                  'pressure', 'rssi', 'ID', 'refresh'
				])
	}
}

def noOp() {}

// parse events into attributes
def parse(String description) {
    log.debug "Parsing '${description}'"
}

def installed() {
	log.info "Installed on ${getHubPlatform()}"
	initialize()
}

def uninstalled() {
	unschedule()
}

def updated() {
	log.info "Updated with settings: ${settings}"
  state.purpleAirStation = getVersionLabel()
  state.hubPlatform = null; getHubPlatform();		// Force hub update if we are updated...just in case
  state.purpleAirVersion = getVersionLabel()
  unschedule()
  initialize()
}

def initialize() {
	log.info getVersionLabel() + " on ${getHubPlatform()} Initializing..."
	if (purpleID) {
		// Schedule the updates
		def t = updateMins ?: '5'
    // TODO fix scheduling
		if (t == '1') {
			runEvery1Minute(getPurpleAirAQI)
		} else {
			"runEvery${t}Minutes"(getPurpleAirAQI)
		}
	}
	if (debugOn) {
		log.debug "Debug logging enabled for 30 minutes"
		runIn(1800, debugOff, [overwrite: true])
	}
	state.isFlagged = 0


	// handle reference temperature / tempOffset automation
	if (settings.referenceTemp != null) {
		if (state.sensorTemp) {
			state.sensorTemp = roundIt(state.sensorTemp, 2)
			state.tempOffset = roundIt(settings.referenceTemp - state.sensorTemp, 2)
			if (debugOn) log.debug "sensorTemp: ${state.sensorTemp}, referenceTemp: ${referenceTemp}, offset: ${state.tempOffset}"
			settings.referenceTemp = null
			device.updateSetting('referenceTemp', "")
			if (isHE) device.clearSetting('referenceTemp')
			//sendEvent(getTemperatureResult(state.sensorTemp))
		} // else, preserve settings.referenceTemp, state.tempOffset will be calculate on the next temperature report
	}

	// handle reference humidity / RHOffset automation
	if (settings.referenceRH != null) {
		if (state.sensorRH) {
			state.sensorRH = roundIt(state.sensorRH, 0)
			state.RHOffset = roundIt(settings.referenceRH - state.sensorRH, 0)
			if (debugOn) log.debug "sensorRH: ${state.sensorRH}, referenceRH: ${referenceRH}, offset: ${state.RHOffset}"
			settings.referenceRH = null
			device.updateSetting('referenceRH', "")
			if (isHE) device.clearSetting('referenceRH')
			//sendEvent(getTemperatureResult(state.sensorTemp))
		} // else, preserve settings.referenceTemp, state.tempOffset will be calculate on the next temperature report
	}

	// handle reference barometric pressure / InHgOffset automation
	if (settings.referenceInHg != null) {
		if (state.sensorInHg) {
			state.sensorInHg = roundIt(state.sensorInHg, 2)
			state.InHgOffset = roundIt(settings.referenceInHg - state.sensorInHg, 2)
			if (debugOn) log.debug "sensorInHg: ${state.sensorInHg}, referenceInHg: ${referenceInHg}, offset: ${state.InHgOffset}"
			settings.referenceInHg = null
			device.updateSetting('referenceInHg', "")
			if (isHE) device.clearSetting('referenceInHg')
			//sendEvent(getTemperatureResult(state.sensorTemp))
		} // else, preserve settings.referenceTemp, state.tempOffset will be calculate on the next temperature report
	}

	sendEvent(name: 'updated', value: "", displayed: false, isStateChange: true)
    sendEvent(name: 'timestamp', value: "initializing", displayed: false, isStateChange: true)	// Send last
	state.isHidden = false

	getPurpleAirAQI()
    log.info "Initialization complete."
}

def runEvery3Minutes(handler) {
	Random rand = new Random()
    int randomSeconds = rand.nextInt(59)
    log.info "AQI seconds: ${randomSeconds}"
	schedule("${randomSeconds} 0/3 * * * ?", handler)
}

// handle commands
def poll() { refresh() }
def refresh() { getPurpleAirAQI() }
def configure() { updated() }

void getPurpleAirAQI() {
	if (!state.purpleAirVersion || (state.purpleAirVersion != getVersionLabel())) {
    	log.warn "Version changed, updating..."
        runIn(2, updated, [overwrite: true])
        return
    }
    if (!settings.purpleID) {
    	sendEvent(name: 'airQualityIndex', value: null, displayed: false)
		sendEvent(name: 'airQuality', value: null, displayed: false)
        sendEvent(name: 'aqi', value: null, displayed: false)
        return
    }
    def params = [
        uri: 		'https://www.purpleair.com',
        path: 		'/json',
        query: 		[show: settings?.purpleID, key: settings?.purpleKey],
		timeout:	30
        // body: ''
    ]
    // If building on/for hubitat, comment out the next line, and uncomment the one after it
	if (state.isST) {
		include 'asynchttp_v1'
    	asynchttp_v1.get(purpleAirResponse, params)
	} else {
    	asynchttpGet(purpleAirResponse, params)
	}
}

void getLocalPurpleAirAQI(){
  if (!state.purpleAirVersion || (state.purpleAirVersion != getVersionLabel())) {
      log.warn "Version changed, updating..."
        runIn(2, updated, [overwrite: true])
        return
  }
  if (!settings.localIp) {
    sendEvent(name: 'airQualityIndex', value: null, displayed: false)
    sendEvent(name: 'airQuality', value: null, displayed: false)
    sendEvent(name: 'aqi', value: null, displayed: false)
    return
  }
  def params = [
      uri: 		settings?.localIp,
      path: 		'/json',
      query: 		[live: "false"],
      timeout:	30
      // body: ''
  ]
  if (state.isST) {
    include 'asynchttp_v1'
    asynchttp_v1.get(purpleAirResponse, params)
  } else {
    asynchttpGet(purpleAirResponse, params)
  }
}
def purpleAirResponse(resp, data) {
	if (resp && (resp.status == 200)) {
		try {
			if (resp.json) {
				//log.trace "Response Status: ${resp.status}\n${resp.json}"
                logDebug("purpleAirResponse() got JSON...")
			} else {
            	// FAIL - no data
                log.warn "purpleAirResponse() no JSON: ${resp.data}"
                return false
            }
		} catch (Exception e) {
  		log.error "purpleAirResponse() - General Exception: ${e}"
        	throw e
            return false
    }
    parsePurpleAir(resp.json)
    return true
  }
  return false
}

def parsePurpleAir(response) {
	if (!response)
  	log.error "Invalid response for PurpleAir request: ${response}"
    return
  }
	def timeStamp = state.isST ? device.currentValue('timestamp') : device.currentValue('timestamp', true)
  if (newest?.toString() == timeStamp) { logDebug("No update..."); return; } // nothing has changed yet

  def resp_data = response.getJson()
  def pm = resp_data['pm2.5_aqi']
  def aqi = roundIt(pm_to_aqi(pm), 0)

  sendEvent(name: 'airQualityIndex', 	value: aqi, displayed: false)
  String p25 = roundIt(pm,1) + ' µg/m³'
  String cond = '??'
  if 		(aqi < 51)  {sendEvent(name: 'message', value: " GOOD: little to no health risk\n (${p25})", descriptionText: 'AQI is GOOD - little to no health risk'); cond = 'GOOD';}
  else if (aqi < 101) {sendEvent(name: 'message', value: " MODERATE: slight risk for some people\n (${p25})", descriptionText: 'AQI is MODERATE - slight risk for some people'); cond = 'MODERATE';}
  else if (aqi < 151) {sendEvent(name: 'message', value: " UNHEALTHY for sensitive groups\n (${p25})", descriptionText: 'AQI is UNHEALTHY for Sensitive Groups'); cond = 'UNHEALTHY';}
  else if (aqi < 201) {sendEvent(name: 'message', value: " UNHEALTHY for most people\n (${p25})", descriptionText: 'AQI is UNHEALTHY for most people'); cond = '*UNHEALTHY*';}
  else if (aqi < 301) {sendEvent(name: 'message', value: " VERY UNHEALTHY: serious effects for everyone (${p25})", descriptionText: 'AQI is VERY UNHEALTHY - serious effects for everyone'); cond = 'VERY UNHEALTHY';}
  else 				{sendEvent(name: 'message', value: " HAZARDOUS: emergency conditions for everyone (${p25})", descriptionText: 'AQI is HAZARDOUS - emergency conditions for everyone'); cond = 'HAZARDOUS';}
	log.info("AQI: ${aqi}")
  sendEvent(name: 'aqi', 	 value: aqi,   descriptionText: "AQI real time is ${aqi}")
  sendEvent(name: 'aqiDisplay', value: "${aqi}\n${cond}", displayed: false)
  sendEvent(name: 'pm',   value: pm,   unit: 'µg/m³', descriptionText: "PM2.5 real time is ${pm}µg/m³")

  def temperature = resp_data["current_temp_f"]
  def humidity = resp_data["current_humidity"]
  def pressure = resp_data["pressure"]
  def dewpoint = resp_data["current_dewpoint_f"]

	// Adjust to reference temperature
	if (temperature != null) {
		if ((state.sensorTemp == null) || (state.sensorTemp != temperature)) state.sensorTemp = temperature
		if (settings.referenceTemp != null) {
			state.tempOffset = roundIt((referenceTemp - temperature), 1)
			if (debugOn) log.debug "sensorTemp: ${temperature}, referenceTemp: ${referenceTemp}, offset: ${state.tempOffset}"
			settings.referenceTemp = null
			device.updateSetting('referenceTemp', "")
			if (isHE) device.clearSetting('referenceTemp')
		}
		def offset = state.tempOffset
		if (offset == null) {
			def temp = device.currentValue('tempOffset')	// convert the old attribute to the new state variable
			offset = (temp != null) ? temp : 0.0
			state.tempOffset = offset
		}
    	if (offset != 0.0) {
    		def v = temperature
    		temperature = roundIt((v + offset), 1)
    	}
	}

	// Adjust to reference humidity
	if (humidity != null) {
		if ((state.sensorRH == null) || (state.sensorRH != humidity)) state.sensorRH = humidity
		if (settings.referenceRH != null) {
			state.RHOffset = roundIt((referenceRH - humidity), 0)
			if (debugOn) log.debug "sensorRH: ${humidity}, referenceRH: ${referenceRH}, offset: ${state.RHOffset}"
			settings.referenceRH = null
			device.updateSetting('referenceRH', "")
			if (isHE) device.clearSetting('referenceRH')
		}
		def offset = state.RHOffset
		if (offset == null) {
			def RH = device.currentValue('RHOffset')	// convert the old attribute to the new state variable
			offset = (RH != null) ? RH : 0
			state.RHOffset = offset
		}
    	if (offset != 0.0) {
    		def v = humidity
    		humidity = roundIt((v + offset), 0)
    	}
	}
    // collect Pressure - may be on one, the other or both sensors
    if (response.results[0]?.pressure?.isNumber()) {
		if (response.results[1]?.pressure?.isNumber()) {
            pressure = roundIt((((response.results[0].pressure.toBigDecimal() + response.results[1].pressure.toBigDecimal()) / 2.0) * 0.02953), 2)
		} else {
			pressure = roundIt((response.results[0].pressure.toBigDecimal() * 0.02953), 2)
		}
	} else if (response.results[1]?.pressure?.isNumber()) {
        pressure = roundIt((response.results[1].pressure.toBigDecimal() * 0.02953), 2)
	}
	// Adjust to reference pressure
	if (pressure != null) {
		if ((state.sensorInHg == null) || (state.sensorInHg != pressure)) state.sensorInHg = pressure
		if (settings.referenceInHg != null) {
			state.InHgOffset = roundIt((settings.referenceInHg - pressure), 2)
			if (debugOn) log.debug "sensorInHg: ${pressure}, referenceInHg: ${referenceInHg}, offset: ${state.InHgOffset}"
			settings.referenceInHg = null
			device.updateSetting('referenceInHg', "")
			if (isHE) device.clearSetting('referenceInHg')
		}
		def offset = state.InHgOffset
		if (offset == null) {
			def InHg = device.currentValue('InHgOffset')	// convert the old attribute to the new state variable
			offset = (InHg != null) ? InHg : 0.0
			state.InHgOffset = offset
		}
    	if (offset != 0.0) {
    		def v = pressure
    		pressure = roundIt((v + offset), 2)
    	}
	}
    if (temperature != null) {
        sendEvent(name: 'temperature', value: temperature, unit: 'F')
        sendEvent(name: 'temperatureDisplay', value: roundIt(temperature, 0), unit: 'F', displayed: false)
    }
    if (humidity != null) {
        sendEvent(name: 'humidity', value: humidity, unit: '%')
    }
    if (pressure != null) {
        sendEvent(name: 'pressure', value: pressure, unit: 'inHg', displayed: false)
        sendEvent(name: 'pressureDisplay', value: pressure+'\ninHg', unit: '', descriptionText: "Barometric Pressure is ${pressure}inHg" )
    }

    def now = new Date(newest).format("h:mm:ss a '\non' M/d/yyyy", location.timeZone).toLowerCase()
    def locLabel = response.results[0]?.Label
    if (response.results[0]?.DEVICE_LOCATIONTYPE != 'inside') {
    	if (single < 2) {
    		locLabel = locLabel + '\nBad data from ' + ((single<0)?'BOTH channels':((single==0)?'Channel B':'Channel A'))
    	}
    } else {
    	if (single < 0) {
        	locLabel = locLabel + '\nBad data from ONLY channel (A)'
        }
    }
    sendEvent(name: 'locationName', value: locLabel)
    sendEvent(name: 'rssi', value: rssi, unit: 'db', descriptionText: "WiFi RSSI is ${rssi}db")
    sendEvent(name: 'ID', value: response.results[0]?.ID, descriptionText: "Purple Air Station ID is ${response.results[0]?.ID}")
    sendEvent(name: 'updated', value: now, displayed: false)
    sendEvent(name: 'timestamp', value: newest.toString(), displayed: false)	// Send last
}

private def pm_to_aqi(pm) {
	def aqi
	if (pm > 500) {
	  aqi = 500;
	} else if (pm > 350.5) {
	  aqi = remap(pm, 350.5, 500.5, 400, 500);
	} else if (pm > 250.5) {
	  aqi = remap(pm, 250.5, 350.5, 300, 400);
	} else if (pm > 150.5) {
	  aqi = remap(pm, 150.5, 250.5, 200, 300);
	} else if (pm > 55.5) {
	  aqi = remap(pm, 55.5, 150.5, 150, 200);
	} else if (pm > 35.5) {
	  aqi = remap(pm, 35.5, 55.5, 100, 150);
	} else if (pm > 12) {
	  aqi = remap(pm, 12, 35.5, 50, 100);
	} else if (pm > 0) {
	  aqi = remap(pm, 0, 12, 0, 50);
	} else { aqi = 0 }
	return aqi;
}

private def pm_to_caqi(pm) {
	def caqi					// based off of (hourly) pm2.5 only
	if (pm > 110) {
	  caqi = 100;
	} else if (pm > 55) {
	  caqi = remap(pm, 55, 110, 75, 100);
	} else if (pm > 30) {
	  caqi = remap(pm, 30, 55, 50, 75);
	} else if (pm > 15) {
	  caqi = remap(pm, 15, 30, 25, 50);
    } else caqi = remap(pm, 0, 15, 0, 25);
	return caqi;
}

private def remap(value, fromLow, fromHigh, toLow, toHigh) {
    def fromRange = fromHigh - fromLow;
    def toRange = toHigh - toLow;
    def scaleFactor = toRange / fromRange;

    // Re-zero the value within the from range
    def tmpValue = value - fromLow;
    // Rescale the value to the to range
    tmpValue *= scaleFactor;
    // Re-zero back to the to range
    return tmpValue + toLow;
}
private def logDebug(msg) { if (debugOn) log.debug(msg) }

def debugOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("debugOn",[value:"false",type:"bool"])
	settings.debugOn = false
}
private roundIt( value, decimals=0 ) {
	return (value == null) ? null : value.toBigDecimal().setScale(decimals, BigDecimal.ROUND_HALF_UP)
}
private roundIt( BigDecimal value, decimals=0) {
    return (value == null) ? null : value.setScale(decimals, BigDecimal.ROUND_HALF_UP)
}
private def getAqiColors() {
	[	// Gradients don't work well - best to keep colors solid for each range
    	[value:   0, color: '#44b621'],		// Green - Good
        [value:  50, color: '#44b621'],
        [value:  51, color: '#f1d801'],		// Yellow - Moderate
        [value: 100, color: '#f1d801'],
        [value: 101, color: '#d04e00'],		// Orange - Unhealthy for Sensitive groups
        [value: 150, color: '#d04e00'],
        [value: 151, color: '#bc2323'],		// Red - Unhealthy
        [value: 200, color: '#bc2323'],
        [value: 201, color: '#800080'],		// Purple - Very Unhealthy
        [value: 300, color: '#800080'],
        [value: 301, color: '#800000']		// Maroon - Hazardous
    ]
}
private def getCaqiColors() {
	// Common Air Quality Index
	[
    	[value:   0, color: '#79bc6a'],		// Green - Very Low
        [value:  24, color: '#79bc6a'],
        [value:  25, color: '#bbcf4c'],		// Chartruese - Low
        [value:  49, color: '#bbcf4c'],
        [value:  50, color: '#eec20b'],		// Yellow - Medium
        [value:  74, color: '#eec20b'],
        [value:  75, color: '#f29305'],		// Orange - High
        [value:  99, color: '#f29305'],
        [value: 100, color: '#e8416f']		// Red - Very High
    ]
}
