#!/bin/bash
#usage: bash exec_container.sh [containername] [socket] [workloadlevel] [duration] [tag]
MYTAG="${5:-test}"
sudo docker exec $1_$2 python run_model.py --workload $3 --duration $4 --idle 0.5 --tag $MYTAG