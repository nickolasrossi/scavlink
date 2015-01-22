#!/bin/bash

# ardupilot SITL script reduced to run just the simulator without mavproxy
DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

# check the instance number to allow for multiple copies of the sim running at once
INSTANCE=0
LOCATION="SunnyvaleBaylands"
OFFSET=0
WIPE_EEPROM=0
VEHICLE=""


usage()
{
cat <<EOF
Usage: sim_only.sh [options]
Options:
    -v VEHICLE          vehicle type (ArduPlane, ArduCopter or APMrover2)
    -I INSTANCE         instance of simulator (default 0)
    -H lat,lon,alt,hdg  latitude, longitude, altitude, heading of home location
    -L LOCATION         if -H is underfined, select home location from Tools/autotest/locations.txt
    -R epsilon-offset   randomize initial location within a box delineated by the offset
    -w                  wipe EEPROM and reload parameters

Note:
    eeprom.bin in the starting directory contains the parameters for your
    simulated vehicle. Always start from the same directory. It is recommended that
    you start in the main vehicle directory for the vehicle you are simulating,
    for example, start in the ArduPlane directory to simulate ArduPlane
EOF
}

while getopts "v:I:L:H:R:wh" opt; do
  case $opt in
    v)
      VEHICLE=$OPTARG
      ;;
    I)
      INSTANCE=$OPTARG
      ;;
    L)
      LOCATION=$OPTARG
      ;;
    H)
      SIMHOME=$OPTARG
      ;;
    R)
      OFFSET=$OPTARG
      ;;
    w)
      WIPE_EEPROM=1
      ;;
    h)
      usage
      exit 0
      ;;
  esac
done
shift $((OPTIND-1))

if [ "$INSTANCE" -eq "0" ]; then
    pkill -f runsim.py
    pkill -f sim_multicopter.py
    pkill -f sim_rover.py
    killall -q ArduPlane.elf ArduCopter.elf APMrover2.elf
fi

# ports
SYSID=$((1+$INSTANCE))
SIMIN_PORT="127.0.0.1:"$((5502+10*$INSTANCE))
SIMOUT_PORT="127.0.0.1:"$((5501+10*$INSTANCE))
FG_PORT="127.0.0.1:"$((5503+10*$INSTANCE))

#set -x

[ -z "$VEHICLE" ] && {
    VEHICLE=$(basename $PWD)
}

autotest=$(dirname $(readlink -e $0))

# get the location information
if [ -z "$SIMHOME" ]; then
    SIMHOME=$(cat $autotest/locations.txt | grep -i "^$LOCATION=" | cut -d= -f2)
fi

[ -z "$SIMHOME" ] && {
    echo "Unknown location $LOCATION"
    usage
    exit 1
}

LAT=`echo $SIMHOME | cut -d',' -f1`
LON=`echo $SIMHOME | cut -d',' -f2`

LAT=`echo "scale=7;$LAT+2*$OFFSET*$RANDOM/32767-$OFFSET" | bc`
LON=`echo "scale=7;$LON+2*$OFFSET*$RANDOM/32767-$OFFSET" | bc`
SIMHOME=$LAT,$LON,`echo $SIMHOME | cut -d',' -f3,4`

echo "Starting up at $LOCATION : $SIMHOME"

cmd="$autotest/../../$VEHICLE/$VEHICLE.elf -I$INSTANCE -PSYSID_THISMAV=1 -PARMING_CHECK=0 -PWPNAV_SPEED=2000 -PWPNAV_ACCEL=500"
if [ $WIPE_EEPROM == 1 ]; then
    cmd="$cmd -w"
fi

case $VEHICLE in
    ArduPlane)
        RUNSIM="$autotest/jsbsim/runsim.py --home=$SIMHOME --simin=$SIMIN_PORT --simout=$SIMOUT_PORT --fgout=$FG_PORT $EXTRA_SIM"
        PARMS="ArduPlane.parm"
        if [ $WIPE_EEPROM == 1 ]; then
            cmd="$cmd -PFORMAT_VERSION=13 -PSKIP_GYRO_CAL=1 -PRC3_MIN=1000 -PRC3_TRIM=1000"
        fi
        ;;
    ArduCopter)
        RUNSIM="$autotest/pysim/sim_multicopter.py --home=$SIMHOME $EXTRA_SIM --simin=$SIMIN_PORT --simout=$SIMOUT_PORT"
        PARMS="copter_params.parm"
        ;;
    APMrover2)
        RUNSIM="$autotest/pysim/sim_rover.py --home=$SIMHOME --simin=$SIMIN_PORT --simout=$SIMOUT_PORT --rate=400 $EXTRA_SIM"
        PARMS="Rover.parm"
        ;;
    *)
        echo "Unknown vehicle simulation type $VEHICLE"
        exit 1
        ;;
esac

echo Starting elf binary:
echo $cmd
$cmd &
sleep 2

echo Starting simulator:
echo $RUNSIM
nice $RUNSIM
