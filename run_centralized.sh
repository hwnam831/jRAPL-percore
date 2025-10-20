#Usage run_.sh [duration] [cap] [app1] [tag] [load2] [seed]
MYTAG="${4:-fair}"
LOAD="${5:-high}"
MYSEED="${6:-1}"
DURATION=$(($1+120))
bash exec_container.sh $3 $LOAD $1 $MYTAG"_"$2 $MYSEED &
sudo java -cp $PWD":"$PWD"/argparse4j-0.9.0.jar" LocalController --parent 10.10.1.1 --policy central --duration $DURATION --cap $2 > $3_$4_central_$2.csv
