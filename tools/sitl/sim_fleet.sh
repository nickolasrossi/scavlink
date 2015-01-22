#!/bin/bash

# Simulate multiple vehicles with SITL.

# To run, create a subdirectory with a fleet name:
# mkdir fleet0
# cd fleet0

# Then, create a file that describes the vehicles in the fleet.
# Each line describes one SITL vehicle that will be started, in the format:
# [instance number] [vehicle type] [location name]
# e.g.
# echo "1  ArduCopter  SunnyvaleBaylands" > fleet.txt
# echo "2  ArduCopter  SunnyvaleBaylands" >> fleet.txt
# echo "3  APMrover2   SunnyvaleBaylands" >> fleet.txt

# Then, run this script in your fleet subdirectory.
# A SITL process for each vehicle will be created.
# Note the TCP port number for each vehicle,
# which will start at 5760 and increment by 10, e.g. 5760, 5770, 5780, etc.
# Set these port numbers in your configuration.


kill_tasks()
{
  killall -q JSBSim lt-JSBSim ArduPlane.elf ArduCopter.elf APMrover2.elf
  pkill -f runsim.py
  pkill -f sim_rover.py
  pkill -f sim_multicopter.py
  exit
}

trap kill_tasks SIGINT

while read -r line
do
  INSTANCE=`echo $line | cut -f1 -d' '`
  VEHICLE=`echo $line | cut -f2 -d' '`
  LOCATION=`echo $line | cut -f3 -d' '`
  DIR=$INSTANCE\_$VEHICLE\_$LOCATION
  mkdir -p $DIR
  pushd $DIR
  cp -n ../eeprom.bin .
  sim_only.sh -I$(($INSTANCE - 1)) -v $VEHICLE -L $LOCATION -R0.00005 $@ &
  sleep 8
  popd
done < fleet.txt

sleep infinity
