#Usage run_.sh [duration] [cap] [app1] [app2] [mytag] [load1] [load2]
MYTAG="${5:-fair}"
first="${6:-high}"
second="${7:-low}"
bash exec_container.sh $3 0 $first $1 $MYTAG_$2 &
bash exec_container.sh $4 1 $second $1 $MYTAG_$2 &
sudo java -cp $PWD":"$PWD"/argparse4j-0.9.0.jar" LocalController --parent 10.10.1.1 --policy ml --duration $1 --cap $2 > $3_$4_hierarchical_$2.csv
