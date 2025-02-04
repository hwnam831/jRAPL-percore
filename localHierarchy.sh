#usage: bash localHierarchy.sh [policy] [cap] [app1] [app2]
bash exec_container.sh $3 0 high 600 & bash exec_container.sh $4 1 low 600 &
sleep 10; sudo java -cp $PWD":"$PWD"/argparse4j-0.9.0.jar" LocalController --policy $1 --duration 600 --cap $2 > local_$1_$2_$3_$4.csv;
sleep 100;