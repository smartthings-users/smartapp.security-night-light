/**
 *  Clever Night Light
 *
 *  Author: Brian Steere
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
    } else {
        modeStopThings()
        state.motionStarted = false
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
