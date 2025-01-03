bash set_freq.sh $1;
taskset -c 0-9 parsecmgmt -a run -p $2 -i native -n 10 & taskset -c 10-19 parsecmgmt -a run -p $3 -i native -n 10 &
sudo java TraceCollector 90 > $2_$3_$1.csv
