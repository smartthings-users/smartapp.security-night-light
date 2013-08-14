/**
 *  Clever Night Light
 *
 *  Author: Brian Steere
 *  Code: https://github.com/smartthings-users/smartapp.security-night-light
 *
 * Copyright (C) 2013 Brian Steere <dianoga7@3dgo.net>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this 
 * software and associated documentation files (the "Software"), to deal in the Software 
 * without restriction, including without limitation the rights to use, copy, modify, 
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to 
 * permit persons to whom the Software is furnished to do so, subject to the following 
 * conditions: The above copyright notice and this permission notice shall be included 
 * in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, 
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A 
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT 
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF 
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE
 * OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

 preferences {

    section("Turn on which things") {
        input "switches", "capability.switch", title: "Things", multiple: true
        input "allowOverride", "enum", title: "Allow manual override?", multiple: false, metadata: [
            values: [
                'Yes',
                'No'
            ]
        ]
    }

    section("Turn on when:") {
        input "startMode", "mode", title: "Mode changes to", required: false
        input "onUntil", "time", title: "Leave on until"
    }

    section("Turn on when there's movement..."){
        input "motion1", "capability.motionSensor", title: "Where?"
        input "motionOnTime", "number", title: "Leave on for how long (minutes)"
    }
}


def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    unschedule()

    initialize()
}

def initialize() {
    subscribe(motion1, "motion", motionHandler)
    subscribe(location)

    if(allowOverride == 'Yes') {
        subscribe(switches, "switch", switchHandler)
    }

    if(location.currentMode == startMode) {
        modeStartThings()
    }
}

def motionHandler(evt) {
    if(!state.modeStarted && location.currentMode == startMode) {
        if (evt.value == "active") {
            log.debug "Saw Motion"

            // Unschedule the stopping of things
            unschedule(motionStopThings)

            startThings()
            state.motionStarted = true;
        }
        else if (evt.value == "inactive") {
            log.debug "No More Motion"
            runIn(motionOnTime * 60, motionStopThings)
        }
    }
}

def changedLocationMode(evt) {
    log.debug "Mode: ${location.currentMode}"
    if(location.currentMode == startMode) {
        modeStartThings()
    }
    else if(state.modeStarted) {
        modeStopThings()
    }
    else if(state.motionStarted) {
        motionStopThings()
    }
}

def modeStartThings() {
    log.debug "Mode starting things"
    state.modeStarted = true
    startThings()

    schedule(getNextTime(), "modeStopThings")
}

def modeStopThings() {
    log.debug "Mode stopping things"
    state.modeStarted = false
    stopThings()

    // Unschedule any additional stopping of things
    unschedule("modeStopThings")
}

def motionStopThings() {
    stopThings()
    state.motionStarted = false
}

def switchHandler(evt) {
    log.debug "Switch: ${evt.value}"

    // Did we activate the things?
    if(state.thingsOn) {
        return
    }

    if(evt.value == 'on') {
        log.debug "Override activated"
        state.override = true;
    }
    else if (evt.value == 'off') {
        log.debug "Override deactivated"
        state.override = false;
    }
}

def startThings() {
    // If override is active, do nothing.
    if(state.override) {
        log.debug "Not starting due to override"
        return
    }

    log.debug "Starting things"
    state.thingsOn = true
    switches.on()
}

def stopThings() {
    // If override is active, do nothing.
    if(state.override) {
        log.debug "Not stopping due to override"
        return
    }

    log.debug "Stopping things"
    switches.off()
    state.thingsOn = false
}

def getNextTime() {
    //return timeTodayAfter(new Date().format('HH:mm'), onUntil)
    return nextOccurrence(onUntil)
}
