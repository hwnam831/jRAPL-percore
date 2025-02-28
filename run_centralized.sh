#Usage run_.sh [duration] [cap] [app1] [app2]
first="${5:-high}"
second="${6:-low}"
bash exec_container.sh $3 0 $first $1 &
bash exec_container.sh $4 1 $second $1 &
sudo java -cp $PWD":"$PWD"/argparse4j-0.9.0.jar" LocalController --parent 10.10.1.1 --policy central --duration $1 --cap $2 > $3_$4_central_$2.csv
