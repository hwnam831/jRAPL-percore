#!/bin/bash
# usage: bash runallchild.sh [lastnode] "command to run"
LAST_NODE=$1
for i in $(seq 2 $LAST_NODE); do
    ssh hwnam831@ow$i "$2" &
done
