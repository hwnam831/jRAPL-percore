#Usage run_.sh [duration] [cap] [app1] [app2] [tag] [load1] [load2] [seed]
MYTAG="${5:-fair}"
first="${6:-high}"
second="${7:-low}"
MYSEED="${8:-1}"
bash exec_container.sh $3 0 $first $1 $MYTAG"_"$2 $MYSEED &
bash exec_container.sh $4 1 $second $1 $MYTAG"_"$2 $MYSEED &
sudo java -cp $PWD":"$PWD"/argparse4j-0.9.0.jar" LocalController --parent 10.10.1.1 --policy central --duration $1 --cap $2 > $3_$4_central_$2.csv
