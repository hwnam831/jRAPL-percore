#Usage run_.sh [policy] [duration] [cap] [tag]
sudo java -cp $PWD":"$PWD"/argparse4j-0.9.0.jar" LocalController --parent 10.10.1.1 --policy $1 --duration $2 --cap $3 > $4_$1_$3.csv
