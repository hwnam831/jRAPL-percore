#Usage: clusterexp.sh [limit] [duration]
TOTALCAP=$(($1*8))
DURATION=$(($2))
#cd /mydata/workspace/faas-profiler;
#./WorkloadInvoker -c warmup.json & sleep 70;
cd /mydata/workspace/jrapl;
mkdir -p results;

ssh hwnam831@ow2 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$DURATION" "$1" stable-diffusion bigcluster_multilevel high 1" 2> /dev/null &
ssh hwnam831@ow4 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$DURATION" "$1" cnn-serving bigcluster_multilevel high 3" 2> /dev/null &
ssh hwnam831@ow6 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$DURATION" "$1" vits-ljs  bigcluster_multilevel high 2" 2> /dev/null &
ssh hwnam831@ow8 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$DURATION" "$1" llama-3.1-8b bigcluster_multilevel high 3" 2> /dev/null &

ssh hwnam831@ow3 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$DURATION" "$1" stable-diffusion bigcluster_multilevel low 1" 2> /dev/null &
ssh hwnam831@ow5 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$DURATION" "$1" cnn-serving bigcluster_multilevel low 3" 2> /dev/null &
ssh hwnam831@ow7 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$DURATION" "$1" vits-ljs  bigcluster_multilevel low 2" 2> /dev/null &
ssh hwnam831@ow9 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$DURATION" "$1" llama-3.1-8b bigcluster_multilevel low 3" 2> /dev/null &

python3 MultilevelController.py --limit $TOTALCAP --duration $DURATION > results/bigcluster_multilevel_$1.csv;
sleep 120;
