#Usage run_.sh [duration] [cap] [app1] [app2]
bash exec_container.sh $3 0 high $1 &
bash exec_container.sh $4 1 low $1 &
sudo java -cp $PWD":"$PWD"/argparse4j-0.9.0.jar" LocalController --parent 10.10.1.1 --policy ml --duration $1 --cap $2 > $3_$4_hierarchical_$2.csv
