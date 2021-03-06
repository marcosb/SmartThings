/**
 *  GreenWave PowerNode 6 Advanced (CHILD DEVICE)
 *
 *  Author: 
 *    Kevin LaFramboise (krlaframboise),  Ulices Soriano (getterdone) tweaked for Greenwave, Marcos B (marcosb) additional GeenWave improvemens
 *
 *  Changelog:
 *
 *    2.1.0 (11/05/2018)
 *      - Update parent when name changes.
 *
 *    2.0.2 (10/16/2018)
 *      - Added support for changing the icon.
 *
 *    2.0.1 (09/30/2018)
 *      - Initial Release
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
	// Automatically generated. Make future change here.
	definition (name: "GreenWave PowerNode 6 Advanced Child", namespace: "marcosb", author: "Marcos B", cstHandler: true) {
	}

	definition (
		name: "GreenWave PowerNode 6 Advanced Child", 
		namespace: "marcosb", 
		author: "Marcos B",
		vid:"generic-switch-power-energy"
	) {
		capability "Actuator"
		capability "Sensor"
		capability "Switch"		
		capability "Outlet"
		// capability "Acceleration Sensor"
		capability "Power Meter"
		capability "Energy Meter"
		capability "Refresh"		
		
		attribute "secondaryStatus", "string"
		attribute "energyTime", "number"
		attribute "energyDuration", "string"
        attribute "switch", "enum", ["on", "off", "poweringOn", "poweringOff"]
        attribute "latestValue", "enum", ["on", "off"]
				
		command "reset"
	}
	
	simulator { }	

	tiles(scale: 2) {
		multiAttributeTile(name:"switch", type: "generic", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#79b821", nextState:"turningOff"
                attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
                attributeState "turningOn", label:'Turning on', icon:"st.switches.switch.on", backgroundColor:"#79b821", nextState:"turningOff"
                attributeState "turningOff", label:'Turning off', icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
			}
			tileAttribute ("device.secondaryStatus", key: "SECONDARY_CONTROL") {
				attributeState "default", label:'${currentValue}'
			}
		}
		valueTile("energy", "device.energy", width: 2, height: 2) {
			state "energy", label:'${currentValue} kWh', backgroundColor: "#cccccc"
		}
		valueTile("power", "device.power", width: 2, height: 2) {
			state "power", label:'${currentValue} W', backgroundColor: "#cccccc"
		}
		standardTile("refresh", "device.refresh", width: 2, height: 2) {
			state "default", label:'Refresh', action: "refresh", icon:"st.secondary.refresh-icon"
		}
		standardTile("reset", "device.reset", width: 2, height: 2) {
			state "default", label:'Reset', action: "reset", icon:"st.secondary.refresh-icon"
		}
	}
	
	preferences { }
}


def installed() { }


def updated() {	
	parent.childUpdated(device.deviceNetworkId)
}


def on() {
	parent.childOn(device.deviceNetworkId)	
}

def off() {
	parent.childOff(device.deviceNetworkId)	
}

def refresh() {
	parent.childRefresh(device.deviceNetworkId)
}

def reset() {
	parent.childReset(device.deviceNetworkId)	
}
