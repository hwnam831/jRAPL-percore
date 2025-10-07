#Usage: clusterexp.sh [policy] [limit] [duration]
TOTALCAP=$(($2*4))
DURATION=$(($3+120))
#cd /mydata/workspace/faas-profiler;
#./WorkloadInvoker -c warmup.json & sleep 70;
cd /mydata/workspace/jrapl;
mkdir -p results;
#LLAMA
bash run_centralized.sh "$DURATION" "$2" cnn-serving "$1"_central high 1 2> /dev/null &
ssh hwnam831@ow2 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$DURATION" "$2" stable-diffusion "$1"_central high 1" 2> /dev/null &
ssh hwnam831@ow4 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$DURATION" "$2" vits-ljs  "$1"_central high 2" 2> /dev/null &
ssh hwnam831@ow5 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$DURATION" "$2" llama-3.1-8b "$1"_central high 3" 2> /dev/null &

python3 ClusterController.py --policy $1 --limit $TOTALCAP --duration $DURATION > results/centralized_$1_$2.csv;
