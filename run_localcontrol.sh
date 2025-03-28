#Usage run_.sh [policy] [duration] [cap]
sudo java -cp $PWD":"$PWD"/argparse4j-0.9.0.jar" LocalController --policy $1 --duration $2 --cap $3 --lr 4.0 --alpha 0.1 > local_$1_$3.csv
