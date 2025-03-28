#!/bin/bash
#usage: bash exec_container.sh [containername] [duration] [tag]
MYTAG="${3:-test}"
sudo docker exec $1"_0" python run_model.py --workload high --duration $2 --continuous --tag $MYTAG &
sudo docker exec $1"_1" python run_model.py --workload low --duration $2 --continuous --tag $MYTAG