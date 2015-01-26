metadata {
	// Automatically generated. Make future change here.
	definition (name: "Aeon Smart Energy Switch w/Cost", author: "Mike") {
		capability "Energy Meter"
        capability "Actuator"
        capability "Switch"
        capability "Power Meter"
        capability "Polling"
        capability "Refresh"
        capability "Sensor"

        attribute "energyCost", "string" 

		command "reset"
        
		fingerprint deviceId: "0x2101", inClusters: " 0x70,0x31,0x72,0x86,0x32,0x80,0x85,0x60"
	}

	// simulator metadata
    simulator {
	    status "on":  "command: 2003, payload: FF"
        status "off": "command: 2003, payload: 00"

        for (int i = 0; i <= 10000; i += 1000) {
            status "power  ${i} W": new physicalgraph.zwave.Zwave().meterV1.meterReport(
                scaledMeterValue: i, precision: 3, meterType: 4, scale: 2, size: 4).incomingMessage()
        }
        for (int i = 0; i <= 100; i += 10) {
            status "energy  ${i} kWh": new physicalgraph.zwave.Zwave().meterV1.meterReport(
                scaledMeterValue: i, precision: 3, meterType: 0, scale: 0, size: 4).incomingMessage()
        }

        // reply messages
        reply "2001FF,delay 100,2502": "command: 2503, payload: FF"
        reply "200100,delay 100,2502": "command: 2503, payload: 00"
	}

	// tile definitions
	tiles {
		standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
            state "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#79b821"
            state "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
        }
        valueTile("power", "device.power", decoration: "flat") {
			state "default", label:'${currentValue}'
		}
		valueTile("energy", "device.energy", decoration: "flat") {
			state "default", label:'${currentValue}'
        }
        valueTile("energyCost", "device.energyCost", decoration: "flat") {
            state "default", label: '${currentValue}'
		}
		standardTile("reset", "device.energy", inactiveLabel: false, decoration: "flat") {
			state "default", label:'reset', action:"reset"
		}
		standardTile("refresh", "device.power", inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}

		main (["switch","power","energy","energyCost"])
		details(["switch","power","energy","energyCost","reset","refresh","configure"])
	        }
                preferences {
                     input "kWhCost", "string", title: "\$/kWh (0.16)", defaultValue: "0.16" as String
                }
}

def parse(String description) {
//	log.debug "Parse received ${description}"
	def result = null
	def cmd = zwave.parse(description, [0x31: 1, 0x32: 1, 0x60: 3])
	if (cmd) {
		result = createEvent(zwaveEvent(cmd))
	}
	if (result) { 
		log.debug "Parse returned ${result?.descriptionText}"
		return result
	} else {
	}
}

def zwaveEvent(physicalgraph.zwave.commands.meterv1.MeterReport cmd) {

    def dispValue
    def newValue
    def formattedValue

        if (cmd.scale == 0) {
        	newValue = Math.round(cmd.scaledMeterValue * 100) / 100
        	if (newValue != state.energyValue) {
        		formattedValue = String.format("%5.2f", newValue)
    			dispValue = "${formattedValue}\nkWh"
                sendEvent(name: "energy", value: dispValue as String, unit: "", descriptionText: "Display Energy: ${newValue} kVh")
                state.energyValue = newValue
                BigDecimal costDecimal = newValue * ( kWhCost as BigDecimal )
                def costDisplay = String.format("%5.2f",costDecimal)
                state.costDisp = "Cost\n\$"+costDisplay
                if (state.display == 1) { sendEvent(name: "energyCost", value: state.costDisp, unit: "", descriptionText: "Display Cost: \$${costDisp}") }
                [name: "energy", value: newValue, unit: "kWh", descriptionText: "Total Energy: ${formattedValue} kWh"]
            }
        }
		else if (cmd.scale == 1) {
            newValue = Math.round( cmd.scaledMeterValue * 100) / 100
            if (newValue != state.energyValue) {
            	formattedValue = String.format("%5.2f", newValue)
    			dispValue = "${formattedValue}\nkVAh"
                sendEvent(name: "energy", value: dispValue as String, unit: "", descriptionText: "Display Energy: ${formattedValue} kVAh")
                state.energyValue = newValue
				[name: "energy", value: newValue, unit: "kVAh", descriptionText: "Total Energy: ${formattedValue} kVAh"]
            }
        }
		else if (cmd.scale==2) {				
        	newValue = Math.round(cmd.scaledMeterValue)		// really not worth the hassle to show decimals for Watts
        	if (newValue != state.powerValue) {
    			dispValue = newValue+"\nWatts"
                sendEvent(name: "power", value: dispValue as String, unit: "", descriptionText: "Display Power: ${newValue} Watts")
                
                if (newValue < state.powerLow) {
                	dispValue = newValue+"\n"+timeString
                	if (state.display == 1) { sendEvent(name: "powerOne", value: dispValue as String, unit: "", descriptionText: "Lowest Power: ${newValue} Watts")	}
                    state.powerLow = newValue
                    state.powerLowDisp = dispValue
                }
                if (newValue > state.powerHigh) {
                	dispValue = newValue+"\n"+timeString
                	if (state.display == 1) { sendEvent(name: "powerTwo", value: dispValue as String, unit: "", descriptionText: "Highest Power: ${newValue} Watts")	}
                    state.powerHigh = newValue
                    state.powerHighDisp = dispValue
                }
                state.powerValue = newValue
                [name: "power", value: newValue, unit: "W", descriptionText: "Total Power: ${newValue} Watts"]
            }
        }
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	// Handles all Z-Wave commands we aren't interested in
	[:]
    log.debug "Capture All $cmd"
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd)
{
    [
        name: "switch", value: cmd.value ? "on" : "off", type: "physical"
    ]
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd)
{
    [
        name: "switch", value: cmd.value ? "on" : "off", type: "digital"
    ]
}

def on() {
    delayBetween([
            zwave.basicV1.basicSet(value: 0xFF).format(),
            zwave.switchBinaryV1.switchBinaryGet().format()
    ])
}

def off() {
    delayBetween([
            zwave.basicV1.basicSet(value: 0x00).format(),
            zwave.switchBinaryV1.switchBinaryGet().format()
    ])
}

def poll() {
    delayBetween([
        zwave.switchBinaryV1.switchBinaryGet().format(),
        zwave.meterV2.meterGet(scale: 0).format(),
        zwave.meterV2.meterGet(scale: 2).format()
    ])
}

def refresh() {
    delayBetween([
        zwave.switchBinaryV1.switchBinaryGet().format(),
        zwave.meterV2.meterGet(scale: 0).format(),
        zwave.meterV2.meterGet(scale: 2).format()
    ])
}

def reset() {
	sendEvent(name: "energyCost", value: "Cost\n--", unit: "")
    return [
            zwave.meterV2.meterReset().format(),
            zwave.meterV2.meterGet(scale: 0).format()
   ]
}

def configure() {
	def cmd = delayBetween([
		zwave.configurationV1.configurationSet(parameterNumber: 101, size: 4, scaledConfigurationValue: 4).format(),   // combined power in watts
		zwave.configurationV1.configurationSet(parameterNumber: 111, size: 4, scaledConfigurationValue: 300).format(), // every 5 min
		zwave.configurationV1.configurationSet(parameterNumber: 102, size: 4, scaledConfigurationValue: 8).format(),   // combined energy in kWh
		zwave.configurationV1.configurationSet(parameterNumber: 112, size: 4, scaledConfigurationValue: 300).format(), // every 5 min
		zwave.configurationV1.configurationSet(parameterNumber: 103, size: 4, scaledConfigurationValue: 0).format(),    // no third report
		zwave.configurationV1.configurationSet(parameterNumber: 113, size: 4, scaledConfigurationValue: 300).format() // every 5 min
	])
	log.debug cmd
	cmd
}
