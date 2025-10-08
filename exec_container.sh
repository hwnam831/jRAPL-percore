#!/bin/bash
#usage: bash exec_container.sh [containername] [socket] [workloadlevel] [duration] [tag] [seed]
MYTAG="${4:-test}"
MYSEED="${5:-1}"
sudo docker exec $1 python amx_run.py --workload $2 --duration $3 --idle 0.5 --tag $MYTAG --config $MYSEED